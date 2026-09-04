package io.github.daisukikaffuchino.han1meviewer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.openani.mediamp.mpv.compose.MpvMediampPlayerSurface

// P5-2a：桌面真引擎渲染（照抄 animeko VideoPlayer.desktop.kt 的 when 分发）。
// 非 DesktopMpv 引擎（Placeholder 等）继续黑 Box；surface 回调在桌面无意义（no-op，见冻结问卷 Q1）。
@Composable
actual fun PlatformVideoSurface(
    engine: PlaybackEngine,
    modifier: Modifier,
    onSurfaceAvailable: (VideoSurface) -> Unit,
    onSurfaceDestroyed: (VideoSurface) -> Unit,
) {
    when (engine) {
        is DesktopMpvPlaybackEngine -> MpvMediampPlayerSurface(engine.mediampPlayer(), modifier)
        else -> Box(modifier.background(Color.Black))
    }
}
