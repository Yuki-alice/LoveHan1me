package lovehan1me.feature.video

import androidx.compose.ui.Modifier

/**
 * iOS 是触摸端：**没有滚轮输入**，因此这里是恒等实现（与 androidMain 同）。
 *
 * ⚠️ 未编译验证：Kotlin/Native 只能 macOS 宿主编译，本文件在 Windows 上无法真编译。
 * 内容为最简单的恒等 actual，风险仅限签名是否与 commonMain 的 expect 一致。
 */
internal actual fun Modifier.playerWheelZoom(
    scale: Float,
    onScaleChange: (Float) -> Unit,
): Modifier = this
