package io.github.daisukikaffuchino.han1meviewer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// P5-1 占位：iOS 真引擎渲染为后续 P5-2 任务（TODO）。
@Composable
actual fun PlatformVideoSurface(
    engine: PlaybackEngine,
    modifier: Modifier,
    onSurfaceAvailable: (VideoSurface) -> Unit,
    onSurfaceDestroyed: (VideoSurface) -> Unit,
) {
    Box(modifier.background(Color.Black))
}
