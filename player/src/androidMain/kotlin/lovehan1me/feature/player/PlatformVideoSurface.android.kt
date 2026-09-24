package lovehan1me.feature.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import org.openani.mediamp.exoplayer.compose.ExoPlayerMediampPlayerSurface

// Gate3-P5：Android 渲染面按引擎分叉。
//
// mediamp-exo：它家 PlayerView 直绑 impl —— aspect 三档真效果（resizeMode）+
// 挂 video effects 后 media3 不上报尺寸时从轨道 Format 兜底（对我们 P4 超分链
// 是免费修复）。VideoSurface 链路与其无关（调用方回调只做 attach/detach，
// 引擎侧为 no-op，故不调）；渲染面尺寸从布局转交（超分 needsUpscale 用）。
//
// mpv 内核（保留例外）：原 SurfaceView 链路原样不动——
// surfaceChanged 的渲染面尺寸转交已提升为 AndroidSurfaceSizeAware 能力接口。
@Composable
actual fun PlatformVideoSurface(
    engine: PlaybackEngine,
    modifier: Modifier,
    onSurfaceAvailable: (VideoSurface) -> Unit,
    onSurfaceDestroyed: (VideoSurface) -> Unit,
) {
    val mediampEngine = engine as? MediampExoPlaybackEngine
    if (mediampEngine != null) {
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
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        onSurfaceAvailable(holder.surface)
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int,
                    ) {
                        // 渲染面对 mpv 要对齐 vo 尺寸（needsUpscale 门控）。
                        (engine as? AndroidSurfaceSizeAware)?.updateSurfaceSize(width, height)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        onSurfaceDestroyed(holder.surface)
                    }
                })
            }
        },
    )
}
