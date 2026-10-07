package lovehan1me.core.domain.state

/**
 * 分页闸门：回答分页加载里两个必须由**单一所有者**回答的问题 ——
 * 「这一次加载能不能开始」，以及「回来的响应还算不算数」。
 *
 * ## 它修的是什么
 * 此前分页序号由 **UI 回调**持有并自增（`{ viewModel.page++; executeSearch() }`），
 * 而 ViewModel 每次调用都无判重地新起一个请求、也没有请求标识。快速滑动时该回调可被
 * 连续触发：序号被连加、多个请求同时在途，先发的响应后到也会照常并入列表 ——
 * 表现为跳页 / 重复条目 / 顺序错乱。
 *
 * ## 为什么抽成独立单元
 * 它不依赖网络、不依赖协程调度，于是"判重"与"陈旧响应"变成**可判定的属性**而不是
 * 时序巧合 —— 可以被确定性测出来（见 `PagingGateTest`）。若把这段逻辑留在 ViewModel 里，
 * 端到端测要拉起 `NetworkRepo` 单例，既难做也不稳定。
 *
 * ## 用法
 * ```
 * private val gate = PagingGate()
 *
 * fun startFirstPage() {                       // 新查询 / 改筛选 / 下拉刷新
 *     val token = gate.restart()               // 作废在途，且立刻拿到新凭证
 *     launch { fetch(page = 1).collect { s -> if (gate.isCurrent(token)) commit(s) } }
 * }
 *
 * fun loadNextPage() {                         // 序列推进
 *     val token = gate.tryBegin() ?: return    // 在途 → 本次触发直接丢弃（判重）
 *     page += 1
 *     launch { fetch(page).collect { s -> if (gate.isCurrent(token)) commit(s) } }
 * }
 * ```
 *
 * 并发假设：调用方串行进入（ViewModel/UI 主线程），故内部不加锁 —— 与本仓库
 * `PageLoadingState` 既有使用面一致。
 */
internal class PagingGate {

    /**
     * 一次加载的凭证。
     *
     * 刻意用独立类型而不是裸 `Long` 或页号：页号与凭证都是数字，传错了编译器不会吭声。
     */
    class Token internal constructor(internal val generation: Long)

    /** 当前代次。[cancel] / [reset] / [restart] 自增它，于是此前发出的凭证全部立即过期。 */
    private var generation: Long = 0

    /** 是否有在途加载。 */
    var inFlight: Boolean = false
        private set

    /**
     * 判重：没有在途加载时领取本轮凭证；**已有在途加载则返回 null**，调用方据此放弃本次触发。
     *
     * 这是"快速滑动只发一个请求"的那道闸。
     */
    fun tryBegin(): Token? {
        if (inFlight) return null
        inFlight = true
        return Token(generation)
    }

    /** 该凭证是否仍然有效（未被 [cancel] / [reset] / [restart] 作废）。 */
    fun isCurrent(token: Token): Boolean = token.generation == generation

    /**
     * 本次加载结束，释放闸门。
     *
     * 只释放**仍然有效**的凭证：被作废过的旧加载若晚到，不能把新一轮的闸门误释放掉
     * （否则新一轮就会同时存在两个在途请求）。
     */
    fun finish(token: Token) {
        if (isCurrent(token)) inFlight = false
    }

    /** 作废所有在途请求（新查询 / 改筛选 / 清屏），闸门同时释放。 */
    fun cancel() {
        generation++
        inFlight = false
    }

    /** 与 [cancel] 同义；语义化命名，"整套分页重新开始"路径专用。 */
    fun reset() = cancel()

    /**
     * 作废在途请求并**立刻**领到新一轮的凭证。
     *
     * 刷新 / 新查询专用：它必须能**打断**在途请求，所以不能走 [tryBegin]（那会被判重挡死）；
     * 同时不能出现"作废了却没领到凭证"的中间态 —— 合成一次调用就没有这道缝。
     */
    fun restart(): Token {
        generation++
        inFlight = true
        return Token(generation)
    }
}
