package lovehan1me.feature.video

import androidx.compose.runtime.Composable

// M3：iOS 电量（UIDevice，需手动开启 batteryMonitoring）随 M-后续接入；当前隐藏。
@Composable
actual fun rememberBatteryState(): BatteryState? = null
