package lovehan1me.feature.video

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier

/**
 * 播放器键盘快捷键的**动作集**（由 UI 侧组装，避免 expect/actual 上挂一长串参数）。
 *
 * 所有回调都用「最新值」语义：快捷键修饰符只安装一次，但回调会随状态变化，
 * 因此实现侧必须用 [rememberUpdatedState] 之类的方式读最新值（见 desktopMain 实现）。
 */
class PlayerKeyActions(
    val onTogglePlay: () -> Unit,
    /** 相对跳转，毫秒增量（负=后退）。 */
    val onSeekBy: (Long) -> Unit,
    /** 音量步进（fine = Shift 微调）。 */
    val onVolumeUp: (fine: Boolean) -> Unit,
    val onVolumeDown: (fine: Boolean) -> Unit,
    val onToggleMute: () -> Unit,
    val onToggleFullscreen: () -> Unit,
    /** 数字键选倍速档（传具体倍率）。 */
    val onSpeedSelected: (Float) -> Unit,
)

/**
 * 安装播放器键盘快捷键。
 *
 * ## 为什么走 expect/actual
 * 需求是"**仅桌面生效**，不得影响 Android / iOS"：两端是触摸设备，装上去徒增与
 * 系统音量键/媒体键打架的风险。所以触摸端给恒等实现（见各 actual）。
 *
 * ## 为什么用 onKeyEvent 而不是 onPreviewKeyEvent
 * [androidx.compose.ui.input.key.onPreviewKeyEvent] 在**向下**传递阶段拦截，
 * 会在**子节点的文本框之前**看到按键 —— 播放器里一旦有输入框，空格就会被吞掉打不进字。
 * [androidx.compose.ui.input.key.onKeyEvent] 只在事件**未被子节点消费**时才冒泡到容器，
 * 天然让文本框优先（"不得抢文本框输入"这条边界因此是机制保证，而不是靠判断）。
 *
 * @param actions 动作集；由调用方用 remember + 最新值捕获组装。
 */
internal expect fun Modifier.playerKeyboardShortcuts(
    actions: PlayerKeyActions,
): Modifier

/**
 * 组装一份**永远读到最新值**的动作集。
 *
 * 快捷键修饰符的安装是一次性的（focusable 节点不重建），如果直接捕获当时的 lambda，
 * 状态变化后按下的仍是旧闭包 —— 这是这类"一次性副作用"最典型的坑。
 */
@androidx.compose.runtime.Composable
internal fun rememberPlayerKeyActions(
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onVolumeUp: (fine: Boolean) -> Unit,
    onVolumeDown: (fine: Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
): PlayerKeyActions {
    val latestPlay by rememberUpdatedState(onTogglePlay)
    val latestSeek by rememberUpdatedState(onSeekBy)
    val latestUp by rememberUpdatedState(onVolumeUp)
    val latestDown by rememberUpdatedState(onVolumeDown)
    val latestMute by rememberUpdatedState(onToggleMute)
    val latestFullscreen by rememberUpdatedState(onToggleFullscreen)
    val latestSpeed by rememberUpdatedState(onSpeedSelected)
    return androidx.compose.runtime.remember {
        PlayerKeyActions(
            onTogglePlay = { latestPlay() },
            onSeekBy = { latestSeek(it) },
            onVolumeUp = { latestUp(it) },
            onVolumeDown = { latestDown(it) },
            onToggleMute = { latestMute() },
            onToggleFullscreen = { latestFullscreen() },
            onSpeedSelected = { latestSpeed(it) },
        )
    }
}
