package lovehan1me.ui.foundation

import androidx.lifecycle.ViewModel
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import lovehan1me.core.util.LogUtil

/**
 * 所有 ViewModel 的推荐基类（对标 animeko `ui-foundation/AbstractViewModel`，按本项目做减法）。
 *
 * 提供两样东西：
 *
 * 1. **[backgroundScope]** —— 独立于 `viewModelScope` 的后台作用域：`SupervisorJob`
 *    （子协程失败不互相牵连）+ 统一异常落点。网络请求、HTML 解析、落盘这类
 *    "不该因为一次失败就把整个 VM 打死"的任务放这里。
 *    默认跑在 `Dispatchers.Default`，不占 UI 线程。
 * 2. **可注入的 `bgContext`** —— 单测时传 `StandardTestDispatcher`，后台协程就跑在
 *    虚拟时间上，不必靠 `runBlocking { delay(...) }` 真等。
 *
 * ### 与 animeko 的差异（有意偏离）
 *
 * animeko 额外实现了 `RememberObserver` 做引用计数，因为它的桌面端拿不到 androidx
 * ViewModel 的生命周期。本项目三端统一走 `androidx.lifecycle.ViewModel`（桌面侧由
 * `AppViewModelStore` 提供 owner），`onCleared()` 时机可靠，因此**不引入**
 * `RememberObserver` —— 那是 Compose runtime 的内部契约，多依赖一层不如少依赖一层。
 *
 * ### 迁移方式
 *
 * ```kotlin
 * class XxxViewModel : AbstractViewModel() {
 *     fun load() {
 *         backgroundScope.launch { /* 网络 / 解析 / 落盘 */ }
 *     }
 * }
 * ```
 *
 * 后续新写的 VM 一律继承它；存量 VM 按改动机会逐步迁，不必一次性全换。
 */
abstract class AbstractViewModel(
    bgContext: CoroutineContext = Dispatchers.Default,
) : ViewModel() {

    val backgroundScope: CoroutineScope = CoroutineScope(
        SupervisorJob() +
            CoroutineExceptionHandler { _, throwable -> onBackgroundException(throwable) } +
            bgContext,
    )

    /** 后台协程未捕获异常的统一落点；默认只记日志，子类可覆盖做上报或降级。 */
    protected open fun onBackgroundException(throwable: Throwable) {
        LogUtil.e("AbstractViewModel", "uncaught in backgroundScope", throwable)
    }

    override fun onCleared() {
        super.onCleared()
        backgroundScope.cancel()
    }
}
