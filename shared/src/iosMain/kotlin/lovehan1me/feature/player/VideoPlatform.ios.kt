package lovehan1me.feature.player

import androidx.compose.runtime.Composable

// M3：iOS 暂无计量网络判定 / 陀螺仪，全部降级。

actual fun isActiveNetworkMetered(): Boolean = false

@Composable
actual fun BindOrientationAutoFullscreen(
    enabled: Boolean,
    onLandscape: () -> Unit,
    onPortrait: () -> Unit,
) {
}
