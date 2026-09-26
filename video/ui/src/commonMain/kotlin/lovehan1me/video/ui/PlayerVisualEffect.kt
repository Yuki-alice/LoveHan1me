package lovehan1me.video.ui

import androidx.compose.ui.Modifier

/**
 * 封面高斯模糊：Android S+ 走 `RenderEffect`，其余平台恒等（不改像素）。
 *
 * 半径是控件侧的构图知识，故归本模块；平台实现由各端 actual 提供。
 */
expect fun Modifier.posterBlur(radiusPx: Float = 32f): Modifier