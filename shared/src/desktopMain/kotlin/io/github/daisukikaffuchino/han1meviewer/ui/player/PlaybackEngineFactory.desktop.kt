package io.github.daisukikaffuchino.han1meviewer.ui.player

// P5-1 占位：桌面真引擎（mpv-libmpv）为后续 P5-2 任务。
actual fun createPlaybackEngine(
    kernel: PlayerKernel,
    allowCast: Boolean,
): PlaybackEngine = PlaceholderPlaybackEngine()
