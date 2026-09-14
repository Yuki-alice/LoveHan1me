package lovehan1me.feature.video

import androidx.compose.ui.Modifier

/**
 * 触摸端（Android / iOS）**没有物理键盘输入** —— 恒等实现。
 *
 * 不是"功能缺失"：装上去反而会和系统的音量键 / 媒体键抢事件，所以按需求保持不启用。
 */
internal actual fun Modifier.playerKeyboardShortcuts(
    actions: PlayerKeyActions,
): Modifier = this
