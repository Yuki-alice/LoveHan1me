package lovehan1me.feature.library

import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.state.PagingGate

/**
 * MyList 分页的**决策核心**。
 *
 * 把「这一页要不要请求」「回来的响应还算不算数」「要不要回退已加载页数」从
 * [MyListSubViewModel]（依赖 `NetworkRepo` 单例，headless 起不来）里抽成不碰网络、不碰协程的
 * 纯逻辑，使这些属性可被确定性断言（同 `mergeSearchPage` / `PagingGate` 的既有做法）。
 *
 * ## 它修的是什么
 * 旧实现里 `favVideoPage` / `watchLaterPage` 由 **UI 回调**持有：
 * ```
 * val page = fav.favVideoPage
 * getMyFavVideoItems(…, page)
 * fav.favVideoPage = page + 1        // 读与写跨两条语句、非原子
 * ```
 * 快速触底两次会读到**同一页** → 重复请求 + 追加重复页；刷新又手工"先置 1 再置 2"，
 * 与加载共用同一个可变序号。同时 [MyListSubViewModel] 用一个共享的 `isRefreshing` bool 判定
 * "追加还是替换"，两轮加载重叠时后到的一轮会读到 `false`，把**旧响应误判为追加**。
 *
 * 修法：分页序列的唯一所有者收归 ViewModel（[PagingGate] + 页号），"是否刷新"由**本轮凭证**
 * 表达（本页是否 `restart` 而来），不再有跨请求的共享 bool。
 */

/**
 * 「加载更多」应当请求的页号；[inFlight] 为真（已有在途加载）时返回 `null`，调用方据此放弃
 * 本次触发（判重）。
 *
 * 纯函数：页号推进与"能否开始"收敛在同一处，避免旧写法那种"先读、再自增"的跨语句非原子步。
 */
internal fun nextPageOrNull(inFlight: Boolean, loadedPageCount: Int): Int? =
    if (inFlight) null else loadedPageCount + 1

/**
 * 一次页响应并入后的状态。
 *
 * @property items 新的列表（已按 `videoCode` 去重）
 * @property loadedPageCount 新的已加载页数
 * @property noMore 本页为空 → 已翻到底
 */
internal data class MyListPageState(
    val items: List<HanimeInfo>,
    val loadedPageCount: Int,
    val noMore: Boolean,
)

/**
 * 把一页响应应用到状态 —— **生产路径**（[MyListSubViewModel] 的收集分支直接调用本函数）。
 *
 * @param gate 分页闸门
 * @param token 本轮加载的凭证
 * @param page 本页页号
 * @param incoming 本页条目
 * @param prevItems 当前列表
 * @param loadedPageCount 当前已加载页数
 * @param isRefresh 本轮是否为刷新（`restart` 而来）——刷新要**替换**列表而非追加
 * @return 新状态；**本轮凭证已过期**（refresh / cancel 之后）则返回 `null`，调用方**不得改动
 *   任何状态**（既不并入列表，也不回退 [loadedPageCount]）。
 */
internal fun applyPageResponse(
    gate: PagingGate,
    token: PagingGate.Token,
    page: Int,
    incoming: List<HanimeInfo>,
    prevItems: List<HanimeInfo>,
    loadedPageCount: Int,
    isRefresh: Boolean,
): MyListPageState? {
    // 陈旧响应：本轮已被 refresh / cancel 作废 —— 不并入、不改页数，直接丢弃。
    if (!gate.isCurrent(token)) return null

    if (incoming.isEmpty()) {
        // 翻到底：不追加、不回退页数（显式 noMore，交给调用方转 NoMoreData）。
        return MyListPageState(
            items = prevItems.distinctBy(HanimeInfo::videoCode),
            loadedPageCount = loadedPageCount,
            noMore = true,
        )
    }

    // 刷新 → 替换（基准为空）；翻页 → 追加（基准为当前列表）。
    val base = if (isRefresh) emptyList() else prevItems
    val merged = (base + incoming).distinctBy(HanimeInfo::videoCode)
    // 页数只增不减：即使有陈旧响应漏过闸门，也不会把已加载页数往回拨。
    return MyListPageState(
        items = merged,
        loadedPageCount = maxOf(loadedPageCount, page),
        noMore = false,
    )
}
