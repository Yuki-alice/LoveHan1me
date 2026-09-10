package lovehan1me.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * M3：播放器 UI 的平台差异点（原 `:app` `VideoPlayerUi` 内联的 Android-only 代码）。
 *
 * - [Modifier.posterBlur]：海报高斯模糊（Android S+ RenderEffect），其他平台恒等。
 * - [isActiveNetworkMetered]：移动数据播放警告用；仅 Android 可判定。
 * - [BindOrientationAutoFullscreen]：陀螺仪自动横竖屏（`OrientationManager`），
 *   仅 Android 生效。
 */
expect fun Modifier.posterBlur(radiusPx: Float = 32f): Modifier

expect fun isActiveNetworkMetered(): Boolean

@Composable
expect fun BindOrientationAutoFullscreen(
    enabled: Boolean,
    onLandscape: () -> Unit,
    onPortrait: () -> Unit,
)
