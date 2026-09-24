package lovehan1me.feature.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

// Android：VideoPlayerUi 原 SurfaceView 代码原样搬入（含 SurfaceHolder 生命周期回调逻辑）。
// surfaceChanged 的渲染面尺寸转交已提升为 AndroidSurfaceSizeAware 能力接口
// （此前硬编码 `engine is MpvPlaybackEngine`；Exo 的 PixelCopy 也需要等大 Bitmap 的尺寸）。
@Composable
actual fun PlatformVideoSurface(
    engine: PlaybackEngine,
    modifier: Modifier,
    onSurfaceAvailable: (VideoSurface) -> Unit,
    onSurfaceDestroyed: (VideoSurface) -> Unit,
) {
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
                        // 渲染面尺寸对多个引擎都有用：Mpv 要对齐 vo 尺寸，
                        // Exo 需要它给 PixelCopy 建"等大 Bitmap"（PixelCopy 不做缩放）。
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
