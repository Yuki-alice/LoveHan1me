package lovehan1me.ui.component

import androidx.compose.runtime.Composable

// desktop：无触感反馈 —— by-design no-op，不是待实现（Gate4-1 确认）。
// 桌面没有触感硬件（触控板/鼠标均无振动器），接什么 API 都是空转；
// DoD 允许"平台本无语义"的 no-op，只要注释标明，即本条。
@Composable
internal actual fun rememberHapticFeedback(): HapticFeedback = HapticFeedback {}
