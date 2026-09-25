package lovehan1me.feature.player

// Gate3-P6：不再是"P5-2 待充实"的占位 —— iOS 渲染面由 PlatformVideoSurface.ios 的
// mediamp AVPlayerLayer 面自持，本类只作调用方契约里的句柄（引擎侧 attach/detach 为 no-op）。
actual class VideoSurface
