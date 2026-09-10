package me.lovehan1me.ui.screen.video

import androidx.compose.runtime.Composable

// M3：桌面无电池概念，返回 null（图标隐藏）。
@Composable
actual fun rememberBatteryState(): BatteryState? = null
