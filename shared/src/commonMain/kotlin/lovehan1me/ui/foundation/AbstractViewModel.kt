package lovehan1me.ui.foundation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

/**
 * 带异常落点的 `viewModelScope.launch`。
 *
 * ### 为什么必须有它
 *
 * `viewModelScope` 的 context 是 `SupervisorJob() + Dispatchers.Main.immediate`，**不含
 * `CoroutineExceptionHandler`**。块内一旦抛出未捕获异常，异常会沿 `SupervisorJob` 冒到根协程，
 * 因无处理器而交给线程的默认未捕获处理器 —— 在 Android 上就是**进程闪退**。
 * `combine` 内部 null Flow 的 NPE 之所以能炸进程，走的正是这条路。
 *
 * 这里把一个 `CoroutineExceptionHandler` 加进本次 launch 的 context：对 `launch` 而言，
 * 处理器从协程自身的 context 里查找，因此能拦下异常、落日志，而不是杀进程。
 *
 * 用法与 `viewModelScope.launch` 一致，多一个 [tag] 供日志检索：
 * ```kotlin
 * launchSafely("VideoVM.getVideo") { NetworkRepo.getHanimeVideo(code).collect { ... } }
 * launchSafely("SearchVM.insertHistory", ioDispatcher) { DatabaseRepo.SearchHistory.insert(it) }
 * ```
 *
 * 注意：它只负责"别崩"，不负责"重试/降级"——真需要恢复语义的地方仍应显式 `try/catch`。
 */
fun ViewModel.launchSafely(
    tag: String,
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> Unit,
): Job = viewModelScope.launch(
    context + CoroutineExceptionHandler { _, throwable ->
        LogUtil.e(tag, "uncaught exception in launchSafely, 已拦下避免进程闪退", throwable)
    },
    block = block,
)
