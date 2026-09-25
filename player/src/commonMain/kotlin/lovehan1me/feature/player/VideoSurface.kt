package lovehan1me.feature.player

/**
 * P5-1：平台视频渲染面抽象（替代 `android.view.Surface`，使 [PlaybackEngine] 可下沉 commonMain）。
 *
 * - androidMain：`actual typealias VideoSurface = android.view.Surface`
 * - desktopMain / iosMain：空类 —— 真渲染面由各端 mediamp 的 Surface 自持
 *   （见 [PlatformVideoSurface]），这个类只作为调用方契约里的句柄存在。
 *
 * Gate3-P5/P6：三端引擎都不再靠它拿渲染面（mediamp 的 `attachSurface` 在基类即 no-op），
 * 但**契约有意保留**：`VideoPlayerUi` 按它回调 attach/detach，删它要一并改 UI 签名，
 * 而收益为零 —— 谁先动 UI 再议。
 */
expect class VideoSurface
