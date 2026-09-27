package lovehan1me.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import lovehan1me.core.util.LogUtil
import org.openani.mediamp.mpv.MpvMediampPlayer
import org.openani.mediamp.mpv.compose.MpvMediampPlayerSurface

/**
 * M3：桌面真渲染（mediamp Skia 面）。
 *
 * Gate3-P5：mediamp 0.5.0 的 `MpvMediampPlayerSurface` 自己经公开
 * `LocalAwtWindow` 定位 SkiaLayer，CMP 垫片 `LocalWindow` 注入链已删
 * （0.3.2 时代 mediamp 编译期引用垫片符号，0.4.0+ 不再需要）。
 * 任一环节缺失（非 mpv 引擎/未预热）回退黑盒，不崩。
 */
@Composable
actual fun PlatformVideoSurface(
    engine: PlaybackEngine,
    modifier: Modifier,
    onSurfaceAvailable: (VideoSurface) -> Unit,
    onSurfaceDestroyed: (VideoSurface) -> Unit,
) {
    val mpvEngine = engine as? DesktopMpvPlaybackEngine
    // 渲染面等 player 预热完成再挂载：之前组合阶段直读 mediampPlayer，
    // mpv 原生初始化把首帧合成也卡住。就绪前先画黑盒，详情页照常先出来。
    var readyPlayer by remember(mpvEngine) { mutableStateOf<MpvMediampPlayer?>(null) }
    LaunchedEffect(mpvEngine) {
        readyPlayer = mpvEngine?.awaitPlayer()
    }
    val player = readyPlayer
    // 渲染面尺寸是超分分辨率门控的唯一真相（mpv 属性给不了：它报的是视频显示尺寸，
    // 与片源比值恒等于 1）。窗口缩放、进全屏都会重报，引擎据此重算要不要挂 shader。
    val sizedModifier = modifier.onSizeChanged { size ->
        mpvEngine?.updateViewportSize(size.width, size.height)
    }
    if (player != null) {
        DisposableEffect(mpvEngine) {
            val surface = VideoSurface()
            onSurfaceAvailable(surface)
            onDispose { onSurfaceDestroyed(surface) }
        }
        // 用顶层 MpvMediampPlayerSurface 而非 MpvMediampPlayerSurfaceProvider().Surface：
        // provider 未覆写 equals，组合里每次 new 都让 Compose 认为接收者变了，
        // Surface body 反复重跑 → mpv Skia 层反复 detach/attach（闪帧/闪退）。
        MpvMediampPlayerSurface(player, sizedModifier)
    } else {
        Box(sizedModifier.background(Color.Black))
    }
}
