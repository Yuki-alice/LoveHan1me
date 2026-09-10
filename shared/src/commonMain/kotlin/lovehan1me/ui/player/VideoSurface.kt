package lovehan1me.ui.player

/**
 * P5-1：平台视频渲染面抽象（替代 `android.view.Surface`，使 [PlaybackEngine] 可下沉 commonMain）。
 *
 * - androidMain：`actual typealias VideoSurface = android.view.Surface`
 * - desktopMain / iosMain：空占位类（P5-2 接真引擎渲染时充实）
 */
expect class VideoSurface
