package lovehan1me.feature.video

import androidx.compose.ui.Modifier

/**
 * iOS **没有物理键盘输入** —— 恒等实现（与 androidMain 同）。
 *
 * ⚠️ 未编译验证：Kotlin/Native 只能 macOS 宿主编译，本文件在 Windows 上无法真编译。
 * 内容为最简单的恒等 actual，风险仅限签名是否与 commonMain 的 expect 一致。
 *
 * 不是"功能缺失"：装上去反而会和系统的音量键 / 媒体键抢事件，所以按需求保持不启用。
 */
internal actual fun Modifier.playerKeyboardShortcuts(
    actions: PlayerKeyActions,
): Modifier = this
