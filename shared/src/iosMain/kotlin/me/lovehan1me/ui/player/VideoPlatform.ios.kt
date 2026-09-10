package me.lovehan1me.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// M3：iOS 暂无 Cast / RenderEffect 模糊 / 计量网络判定 / 陀螺仪，全部降级。

@Composable
actual fun CastRouteButton(
    contentDescription: String,
    modifier: Modifier,
) {
    Box(modifier)
}

actual fun Modifier.posterBlur(radiusPx: Float): Modifier = this

actual fun isActiveNetworkMetered(): Boolean = false

@Composable
actual fun BindOrientationAutoFullscreen(
    enabled: Boolean,
    onLandscape: () -> Unit,
    onPortrait: () -> Unit,
) {
}
