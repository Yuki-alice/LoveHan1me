package lovehan1me.feature.video

import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import lovehan1me.feature.player.PlayerDefaults
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/** 键盘快进/快退步长：与双击手势的 10s 保持一致，免得"同一件事两种步长"。 */
private const val KEY_SEEK_STEP_MS = 10_000L

/**
 * 桌面端播放器快捷键（键位表见 `docs` 与提交说明）：
 * 空格/K 播放暂停 ｜ J/← 后退 ｜ L/→ 前进 ｜ ↑/↓ 音量（Shift 微调）｜ M 静音 ｜ F 全屏 ｜ 数字键 倍速档。
 *
 * 用 [onKeyEvent] 而不是 onPreviewKeyEvent：**只在子节点未消费时**才轮到容器，
 * 于是播放器内的输入框永远优先拿到按键（"不抢文本框输入"是机制保证）。
 */
internal actual fun Modifier.playerKeyboardShortcuts(
    actions: PlayerKeyActions,
): Modifier = this
    .focusable()
    .onKeyEvent { event ->
        // 只处理按下，避免一次按键触发两次（KeyDown/KeyUp 都会走到这里）
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (event.key) {
            Key.Spacebar, Key.K -> actions.onTogglePlay()

            // 方向键按 RTL 不该翻转：播放器里的"左=后退"是内容语义，不是布局语义
            Key.J, Key.DirectionLeft -> actions.onSeekBy(-KEY_SEEK_STEP_MS)
            Key.L, Key.DirectionRight -> actions.onSeekBy(KEY_SEEK_STEP_MS)

            Key.DirectionUp -> actions.onVolumeUp(event.isShiftPressed)
            Key.DirectionDown -> actions.onVolumeDown(event.isShiftPressed)
            Key.M -> actions.onToggleMute()
            Key.F -> actions.onToggleFullscreen()

            Key.One -> actions.selectSpeedIndex(0)
            Key.Two -> actions.selectSpeedIndex(1)
            Key.Three -> actions.selectSpeedIndex(2)
            Key.Four -> actions.selectSpeedIndex(3)
            Key.Five -> actions.selectSpeedIndex(4)
            Key.Six -> actions.selectSpeedIndex(5)
            Key.Seven -> actions.selectSpeedIndex(6)
            Key.Eight -> actions.selectSpeedIndex(7)
            Key.Nine -> actions.selectSpeedIndex(8)
            Key.Zero -> actions.selectSpeedIndex(9)

            else -> return@onKeyEvent false
        }
        true
    }

/** 数字键 → 第 [index] 档倍速（越界忽略，避免"按了 8 结果跳到最后一档"）。 */
private fun PlayerKeyActions.selectSpeedIndex(index: Int) {
    val speed = PlayerDefaults.speeds.getOrNull(index) ?: return
    onSpeedSelected(speed)
}
