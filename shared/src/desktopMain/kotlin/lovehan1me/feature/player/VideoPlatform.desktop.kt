package lovehan1me.feature.player

import androidx.compose.runtime.Composable

// M3：桌面暂无计量网络判定 / 陀螺仪，全部降级。
// （播放器窗口全屏与画中画随 M-后续经 VideoPageHost 增强。）

actual fun isActiveNetworkMetered(): Boolean = false

@Composable
actual fun BindOrientationAutoFullscreen(
    enabled: Boolean,
    onLandscape: () -> Unit,
    onPortrait: () -> Unit,
) {
}
