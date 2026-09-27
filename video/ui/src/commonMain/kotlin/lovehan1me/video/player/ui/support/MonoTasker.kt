/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.support

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * 同一时刻只留最后一个任务的执行器：新任务进来先取消上一个。
 *
 * 键盘长按、双击快进这类交互必须有它，否则"按住 3 秒"会排出一串 seek，
 * 松手后还会继续跳。
 *
 * 注意：本函数非线程安全。需要线程安全版本时考虑 `SingleTaskExecutor`。
 */
@Stable
interface MonoTasker {
    val isRunning: StateFlow<Boolean>

    fun launch(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit,
    ): Job

    fun <R> async(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> R,
    ): Deferred<R>

    /** 等上一个任务完成后再执行。注意：非线程安全。 */
    fun launchNext(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit,
    )

    fun cancel(cause: CancellationException? = null)

    suspend fun cancelAndJoin()

    suspend fun join()
}

fun MonoTasker(
    scope: CoroutineScope,
): MonoTasker = object : MonoTasker {
    var job: Job? = null

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    override fun launch(
        context: CoroutineContext,
        start: CoroutineStart,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        job?.cancel()
        val newJob = scope.launch(context, start, block).apply {
            invokeOnCompletion {
                if (job === this) {
                    _isRunning.value = false
                }
            }
        }.also { job = it }
        _isRunning.value = true

        return newJob
    }

    override fun <R> async(
        context: CoroutineContext,
        start: CoroutineStart,
        block: suspend CoroutineScope.() -> R,
    ): Deferred<R> {
        job?.cancel()
        val deferred = scope.async(context, start, block).apply {
            invokeOnCompletion {
                if (job === this) {
                    _isRunning.value = false
                }
            }
        }
        job = deferred
        _isRunning.value = true
        return deferred
    }

    override fun launchNext(
        context: CoroutineContext,
        start: CoroutineStart,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        val existingJob = job
        job = scope.launch(context, start) {
            try {
                existingJob?.join()
                block()
            } catch (e: CancellationException) {
                existingJob?.cancel()
                throw e
            }
        }.apply {
            invokeOnCompletion {
                if (job === this) {
                    _isRunning.value = false
                }
            }
        }
        _isRunning.value = true
    }

    override fun cancel(cause: CancellationException?) {
        job?.cancel(cause) // 由完成回调把 _isRunning 置回 false
    }

    override suspend fun cancelAndJoin() {
        job?.run {
            cancel()
            join()
        }
    }

    override suspend fun join() {
        job?.join()
    }
}

@Composable
inline fun rememberUiMonoTasker(
    crossinline getContext: @DisallowComposableCalls () -> CoroutineContext = { EmptyCoroutineContext },
): MonoTasker {
    val uiScope = rememberCoroutineScope(getContext)
    return remember(uiScope) { MonoTasker(uiScope) }
}
