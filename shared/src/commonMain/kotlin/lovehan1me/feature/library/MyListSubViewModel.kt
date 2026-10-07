package lovehan1me.feature.library

import lovehan1me.data.NetworkRepo
import lovehan1me.data.SettingsRepository
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.MyListItems
import lovehan1me.core.domain.model.MyListType
import lovehan1me.core.domain.state.PageLoadingState
import lovehan1me.core.domain.state.PagingGate
import lovehan1me.core.domain.state.WebsiteState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 收藏 / 稍后再看（在线）两个子 ViewModel 的公共基类。
 *
 * 分页序列（页号 + 判重 + 作废）由本类**唯一持有**并实现 [MyListPagingController]，子类只提供
 * [listType] 与可选的 [onPageSuccess] 钩子。
 *
 * 旧实现把页号暴露给 UI、并用共享的 `isRefreshing` bool 区分"追加 / 替换"，是并发错乱的根因
 * （见 `MyListPaging.kt` 的 `applyPageResponse` KDoc）。这里改为：页号私有；"是否刷新"由**本轮
 * 凭证**表达；陈旧响应在写状态前先过 [PagingGate.isCurrent]。
 */
abstract class MyListSubViewModel(
    private val scope: CoroutineScope,
    private val listType: MyListType,
) : MyListPagingController {

    protected val itemsStateFlow: MutableStateFlow<PageLoadingState<MyListItems<HanimeInfo>>> =
        MutableStateFlow(PageLoadingState.Loading)

    protected val itemsFlow = MutableStateFlow(emptyList<HanimeInfo>())

    protected val mutableLoadedPageCount = MutableStateFlow(0)
    override val loadedPageCount = mutableLoadedPageCount.asStateFlow()

    protected val mutableIsLoadingMore = MutableStateFlow(false)
    override val isLoadingMore = mutableIsLoadingMore.asStateFlow()

    /**
     * 分页闸门：判重 / 请求标识 / 作废，见 [PagingGate]。
     *
     * 分页序列的唯一所有者在**这里**，不再由 UI 回调持有。
     */
    private val pagingGate = PagingGate()

    /** 当前已加载页数；下一页 = 本值 + 1。 */
    private var currentPage = 0

    /** 一页成功响应后的钩子（如 [FavSubViewModel] 捕获 csrfToken）。 */
    protected open fun onPageSuccess(info: MyListItems<HanimeInfo>) {}

    /**
     * 加载下一页。**判重 + 页号推进 + 发起**全部收在这里。
     *
     * @return 本次是否真的发起；false = 已有在途请求，本次触发被判重丢弃。
     */
    override fun loadNextPage(): Boolean {
        // 判重：在途时 nextPageOrNull 返回 null，本次触发直接丢弃。
        val page = nextPageOrNull(inFlight = pagingGate.inFlight, loadedPageCount = currentPage)
            ?: return false
        // 单线程串行进入：上一行已确认无在途，tryBegin 必能领到凭证（留 `?:` 作防御）。
        val token = pagingGate.tryBegin() ?: return false
        currentPage = page
        launchPage(page = page, token = token, isRefresh = false)
        return true
    }

    /**
     * 下拉刷新：作废在途请求，从第一页重新加载。
     *
     * 走 [PagingGate.restart] 而非 [PagingGate.tryBegin]：刷新必须能**打断**在途请求，不能被
     * 判重挡死，否则下拉时若正好有翻页在跑，刷新会静默失效。
     */
    override fun refresh() {
        clearMyListItems()
        currentPage = 1
        launchPage(page = 1, token = pagingGate.restart(), isRefresh = true)
    }

    /**
     * 真正发起一页加载。
     *
     * [token] 是本次加载的凭证：响应回来先过 [applyPageResponse] 内的 [PagingGate.isCurrent]，
     * 过期就直接丢弃 —— 不写列表、不改页数、不回退 [loadedPageCount]。[PagingGate.finish] 放
     * `finally`，协程异常 / 取消也要释放闸门，否则翻页会永久卡死（对已作废凭证是 no-op）。
     */
    private fun launchPage(page: Int, token: PagingGate.Token, isRefresh: Boolean) {
        mutableIsLoadingMore.value = !isRefresh && itemsFlow.value.isNotEmpty()
        scope.launch {
            try {
                NetworkRepo.getMyListItems(SettingsRepository.savedUserId, listType, page)
                    .collect { state ->
                        when (state) {
                            is PageLoadingState.Success -> {
                                val applied = applyPageResponse(
                                    gate = pagingGate,
                                    token = token,
                                    page = page,
                                    incoming = state.info.hanimeInfo,
                                    prevItems = itemsFlow.value,
                                    loadedPageCount = mutableLoadedPageCount.value,
                                    isRefresh = isRefresh,
                                )
                                // applied == null → 陈旧响应，当前轮已被 refresh/cancel 作废；
                                // 不写任何状态。
                                if (applied != null) {
                                    onPageSuccess(state.info)
                                    itemsStateFlow.value = state
                                    itemsFlow.value = applied.items
                                    mutableLoadedPageCount.value = applied.loadedPageCount
                                    if (applied.noMore) {
                                        itemsStateFlow.value = PageLoadingState.NoMoreData
                                    }
                                    mutableIsLoadingMore.value = false
                                }
                            }

                            is PageLoadingState.Loading -> {
                                if (pagingGate.isCurrent(token)) itemsStateFlow.value = state
                            }

                            else -> {
                                if (pagingGate.isCurrent(token)) {
                                    itemsStateFlow.value = state
                                    mutableIsLoadingMore.value = false
                                }
                            }
                        }
                    }
            } finally {
                pagingGate.finish(token)
            }
        }
    }

    protected fun <T, R> deleteItem(
        deleteCall: suspend () -> kotlinx.coroutines.flow.Flow<WebsiteState<T>>,
        emitTo: MutableSharedFlow<WebsiteState<R>>,
        position: Int,
        mapState: (WebsiteState<T>) -> WebsiteState<R>,
        isSuccess: (WebsiteState<T>) -> Boolean = { it is WebsiteState.Success },
    ) {
        scope.launch {
            deleteCall().collect { deleteState ->
                emitTo.emit(mapState(deleteState))
                itemsFlow.update { list ->
                    if (isSuccess(deleteState)) {
                        list.toMutableList().apply { removeAt(position) }
                    } else list
                }
            }
        }
    }

    override fun clearMyListItems() {
        pagingGate.reset() // 作废在途请求，释放闸门
        currentPage = 0
        mutableIsLoadingMore.value = false
        mutableLoadedPageCount.value = 0
        itemsFlow.value = emptyList()
        itemsStateFlow.value = PageLoadingState.Loading
    }
}
