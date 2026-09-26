package lovehan1me.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import org.openani.mediamp.exoplayer.compose.ExoPlayerMediampPlayerSurface

// Gate3-P5：Android 渲染面换 mediamp 自家 PlayerView（直绑 impl）。
// Gate3-P6：mpv 内核已砍，原 SurfaceView 分支（`SurfaceHolder.Callback` +
// `MPVLib.attachSurface`）已无引擎可走，随之删除。
//
// 现状：
// - aspect 三档真效果走它家 `resizeMode`；挂 video effects 后 media3 不上报尺寸时
//   它从选中轨道 Format 兜底（对 P4 超分链是免费修复）。
// - 渲染面尺寸从布局转交（[AndroidSurfaceSizeAware]），超分 `needsUpscale` 用。
// - `VideoSurface` 契约在本端**不回调**：mediamp 的 `attachSurface/detachSurface`
//   在基类即 no-op，而 Android 的 `VideoSurface` 是 `android.view.Surface` 的
//   typealias（不是可 new 的类），造"假实例"只能调 deprecated 的空构造 ——
//   没有消费者还去踩废弃 API 不值得。iOS/桌面能造假实例是因为它们是真类。
@Composable
actual fun PlatformVideoSurface(
    engine: PlaybackEngine,
    modifier: Modifier,
    onSurfaceAvailable: (VideoSurface) -> Unit,
    onSurfaceDestroyed: (VideoSurface) -> Unit,
) {
    val mediampEngine = engine as? MediampExoPlaybackEngine
    if (mediampEngine == null) {
        // 理论上不可达（工厂恒返 mediamp-exo），保底黑盒而不是崩。
        Box(modifier.background(Color.Black))
        return
    }
    Box(
        modifier
            .onSizeChanged { size ->
                mediampEngine.updateSurfaceSize(size.width, size.height)
            },
    ) {
        ExoPlayerMediampPlayerSurface(
            mediampPlayer = mediampEngine.mediampPlayer,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
