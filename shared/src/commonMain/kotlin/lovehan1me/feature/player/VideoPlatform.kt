package lovehan1me.feature.player

import androidx.compose.runtime.Composable

/**
 * M3：播放器 UI 的平台差异点（原 `:app` `VideoPlayerUi` 内联的 Android-only 代码）。
 *
 * - [isActiveNetworkMetered]：移动数据播放警告用；仅 Android 可判定。
 * - [BindOrientationAutoFullscreen]：陀螺仪自动横竖屏（`OrientationManager`），
 *   仅 Android 生效。
 */
expect fun isActiveNetworkMetered(): Boolean

@Composable
expect fun BindOrientationAutoFullscreen(
    enabled: Boolean,
    onLandscape: () -> Unit,
    onPortrait: () -> Unit,
)
