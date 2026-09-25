package lovehan1me.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.openani.mediamp.avkit.AVKitMediampPlayerSurface

// Gate3-P5：iOS 渲染面换 mediamp 自家 Surface（AVPlayerLayer 嵌 UIKitView）。
//
// aspect 三档真效果走它家 videoGravity（与旧实现同机制，状态来源统一到
// mediamp VideoAspectRatio feature）。VideoSurface 链路与其无关（引擎
// attach/detach 为 no-op），假实例保住调用方契约。
// （Gate3-P6：旧 IosAVPlaybackEngine 已删，原先"删引擎时同步删本注释"的待办已完成。）
@Composable
actual fun PlatformVideoSurface(
    engine: PlaybackEngine,
    modifier: Modifier,
    onSurfaceAvailable: (VideoSurface) -> Unit,
    onSurfaceDestroyed: (VideoSurface) -> Unit,
) {
    val mediampEngine = engine as? MediampAvPlaybackEngine
    if (mediampEngine == null) {
        Box(modifier.background(Color.Black))
        return
    }
    DisposableEffect(mediampEngine) {
        val surface = VideoSurface()
        onSurfaceAvailable(surface)
        onDispose { onSurfaceDestroyed(surface) }
    }
    AVKitMediampPlayerSurface(
        mediampPlayer = mediampEngine.mediampPlayer,
        modifier = modifier.background(Color.Black),
    )
}
