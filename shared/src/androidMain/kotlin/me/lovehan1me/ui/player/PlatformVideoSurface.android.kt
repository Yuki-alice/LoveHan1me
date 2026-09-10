package me.lovehan1me.ui.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

// Android：VideoPlayerUi 原 SurfaceView 代码原样搬入（含 SurfaceHolder 生命周期回调逻辑）。
// surfaceChanged 里的 Mpv updateSurfaceSize 特判一并内聚于此（同包同源集，直接引用）。
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
                        if (engine is MpvPlaybackEngine) {
                            engine.updateSurfaceSize(width, height)
                        }
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        onSurfaceDestroyed(holder.surface)
                    }
                })
            }
        },
    )
}
