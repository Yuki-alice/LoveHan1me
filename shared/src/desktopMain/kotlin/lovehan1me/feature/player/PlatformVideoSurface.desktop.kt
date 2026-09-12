package lovehan1me.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.LocalWindow
import lovehan1me.core.util.LogUtil
import org.openani.mediamp.mpv.MpvMediampPlayer
import org.openani.mediamp.mpv.compose.MpvMediampPlayerSurfaceProvider

/**
 * M3：桌面真渲染（mediamp Skia 面）。
 *
 * 链路：`DesktopWindowHolder.window`（Main.kt `Window {}` 作用域注入）
 * → 垫片 `LocalWindow`（CMP 1.12 已移除，见 `androidx/compose/ui/window/LocalWindow.kt`）
 * → mediamp `findSkiaLayer(window)` → `createSkiaInterop` → Ring 绘制。
 * 任一环节缺失（无窗口/非 mpv 引擎）回退黑盒，不崩。
 */
@Composable
actual fun PlatformVideoSurface(
    engine: PlaybackEngine,
    modifier: Modifier,
    onSurfaceAvailable: (VideoSurface) -> Unit,
    onSurfaceDestroyed: (VideoSurface) -> Unit,
) {
    val window = DesktopWindowHolder.window
    val mpvEngine = engine as? DesktopMpvPlaybackEngine
    // 渲染面等 player 预热完成再挂载：之前组合阶段直读 mediampPlayer，
    // mpv 原生初始化把首帧合成也卡住。就绪前先画黑盒，详情页照常先出来。
    var readyPlayer by remember(mpvEngine) { mutableStateOf<MpvMediampPlayer?>(null) }
    LaunchedEffect(mpvEngine) {
        readyPlayer = mpvEngine?.awaitPlayer()
    }
    val player = readyPlayer
    if (window != null && mpvEngine != null && player != null) {
        DisposableEffect(mpvEngine) {
            val surface = VideoSurface()
            onSurfaceAvailable(surface)
            onDispose { onSurfaceDestroyed(surface) }
        }
        CompositionLocalProvider(LocalWindow provides window) {
            // 渲染面随 engine 同 jar（0.3.2），版本天然对齐；缺的是 CMP 1.12
            // 移除的 LocalWindow——本文件同模块垫片已补回。
            MpvMediampPlayerSurfaceProvider().Surface(player, modifier)
        }
    } else {
        Box(modifier.background(Color.Black))
    }
}
