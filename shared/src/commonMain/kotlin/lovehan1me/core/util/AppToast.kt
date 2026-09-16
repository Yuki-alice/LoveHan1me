package lovehan1me.core.util

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.launch
import lovehan1me.Res
import lovehan1me.ic_check
import lovehan1me.ic_close
import lovehan1me.ic_error_outline
import lovehan1me.ic_info
import lovehan1me.ic_warning
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * 应用内唯一的 Toast 入口（官方 M3 `Snackbar` 实现）。
 *
 * 调用方可以位于 Compose、ViewModel 或普通回调；请求切主线程投递，
 * 无 Host 时进有界队列（最多 10 条，超了丢最旧的）—— 与老 Sonner 实现同语义。
 * 不要直接使用 Android [android.widget.Toast]。
 *
 * 类型只做**图标区分**（成功/提示/警告/错误），容器与文字一律走 M3 Snackbar
 * 规范色（inverseSurface/inverseOnSurface），不自创配色。
 */
object AppToast {
    /** Toast 类型：只决定图标，不决定容器色（M3 规范）。 */
    enum class Kind {
        Success,
        Info,
        Warning,
        Error,
    }

    private data class Request(
        val message: String,
        val kind: Kind,
    )

    private const val MAX_PENDING_REQUESTS = 10

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val requests = Channel<Request>(
        capacity = MAX_PENDING_REQUESTS,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )

    /** 全局 Toast 宿主，挂到应用根 Compose 内容下（与老 AppToast.Host() 同位置）。 */
    @Composable
    fun Host(modifier: Modifier = Modifier) {
        val hostState = remember { SnackbarHostState() }
        // 消费是严格串行的：showSnackbar 挂起直到当前条消失，
        // 天然形成队列语义；currentKind 与当前条一一对应，无竞态。
        var currentKind by remember { mutableStateOf(Kind.Info) }
        LaunchedEffect(hostState) {
            for (request in requests) {
                currentKind = request.kind
                hostState.showSnackbar(
                    message = request.message,
                    duration = if (request.kind == Kind.Error) {
                        SnackbarDuration.Long
                    } else {
                        SnackbarDuration.Short
                    },
                )
            }
        }

        SnackbarHost(
            hostState = hostState,
            modifier = modifier,
        ) { data ->
            Snackbar(
                dismissAction = {
                    IconButton(onClick = { data.dismiss() }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = null,
                        )
                    }
                },
            ) {
                Icon(
                    painter = painterResource(currentKind.iconRes),
                    contentDescription = null,
                    tint = currentKind.iconTint(),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = data.visuals.message)
            }
        }
    }

    fun success(message: String?) = show(message, Kind.Success)

    fun info(message: String?) = show(message, Kind.Info)

    fun warning(message: String?) = show(message, Kind.Warning)

    fun error(message: String?) = show(message, Kind.Error)

    private fun show(message: String?, kind: Kind) {
        val text = message?.trim().orEmpty()
        if (text.isEmpty()) return
        mainScope.launch {
            @Suppress("UNUSED_VARIABLE")
            val result: ChannelResult<Unit> = requests.trySend(Request(text, kind))
        }
    }

    private val Kind.iconRes: DrawableResource
        get() = when (this) {
            Kind.Success -> Res.drawable.ic_check
            Kind.Info -> Res.drawable.ic_info
            Kind.Warning -> Res.drawable.ic_warning
            Kind.Error -> Res.drawable.ic_error_outline
        }

    @Composable
    private fun Kind.iconTint() = when (this) {
        Kind.Success -> MaterialTheme.colorScheme.inversePrimary
        Kind.Info -> MaterialTheme.colorScheme.inverseOnSurface
        Kind.Warning -> MaterialTheme.colorScheme.tertiary
        Kind.Error -> MaterialTheme.colorScheme.error
    }
}
