package lovehan1me.feature.video

import androidx.compose.ui.Modifier

/**
 * 桌面端"Ctrl+滚轮"模拟双指缩放（**仅缩放**：与触摸侧的双指手势同一套语义）。
 *
 * ## 为什么是 expect/actual
 * 判定 Ctrl 要读 `PointerEvent.keyboardModifiers.isCtrlPressed`，而该属性**只在 skiko/桌面
 * 变体里解析得到**：写在 commonMain 会直接 `Unresolved reference`（已用 ui-1.10.3 的 klib
 * 元数据确认符号只出现在 skiko 侧）。所以把这段下沉到 desktopMain，触摸端（Android / iOS）
 * 给一个恒等的 actual —— 触摸平台本来也没有滚轮，语义上是"没有这个输入"，不是"功能缺失"。
 *
 * @param scale 当前缩放倍率（作为 pointerInput 的 key，变化时重启手势监听）
 * @param onScaleChange 增量回调，与双指缩放共用同一个 clamp（见 VIDEO_SCALE_MIN/MAX）
 */
internal expect fun Modifier.playerWheelZoom(
    scale: Float,
    onScaleChange: (Float) -> Unit,
): Modifier
