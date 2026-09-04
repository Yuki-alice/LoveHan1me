package io.github.daisukikaffuchino.han1meviewer.ui.player

// P5-1 占位：iOS 真引擎（AVPlayer）为后续 P5-2 任务。
actual fun createPlaybackEngine(
    kernel: PlayerKernel,
    allowCast: Boolean,
): PlaybackEngine = PlaceholderPlaybackEngine()
