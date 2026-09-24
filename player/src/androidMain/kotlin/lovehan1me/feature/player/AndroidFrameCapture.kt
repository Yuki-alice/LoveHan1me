package lovehan1me.feature.player

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import lovehan1me.core.util.LogUtil
import kotlin.coroutines.resume

/**
 * 渲染面尺寸感知（Android 专用）。
 *
 * 为什么需要它：`PixelCopy` 要求**目标 Bitmap 的尺寸与渲染面尺寸完全一致**，
 * 否则直接失败（它不做缩放）。而 `PlaybackEngine.attachSurface` 只拿到一个
 * `android.view.Surface`，**从 Surface 本身读不出宽高**。
 *
 * 于是把 `PlatformVideoSurface` 里 `SurfaceHolder.Callback.surfaceChanged` 拿到的
 * 宽高转交给引擎。此前这段逻辑是**硬编码只给 MpvPlaybackEngine** 的
 * （`if (engine is MpvPlaybackEngine)`），这里提升为一个能力接口，让 Exo 也能用。
 */
internal interface AndroidSurfaceSizeAware {
    fun updateSurfaceSize(width: Int, height: Int)
}

/**
 * Bitmap → ARGB `IntArray`（0xAARRGGBB，长度 = 宽×高），并**缩到长边不超过 [targetLongEdge]**。
 *
 * 为什么必须在这里缩、而不是交给公共层的 `FrameScaler`：
 * 1080p 一帧的 `IntArray` 就是 8 MB，而录制要同时持有几十帧
 * （`GifCapturePolicy` 的内存预算按**输出尺寸**估算）。
 * 平台侧解码器本来就带缩放能力（mpv 的 `grabThumbnail(dim)`、`Bitmap.createScaledBitmap`），
 * 先缩再转数组能把峰值内存降一个数量级。
 *
 * @return null 表示位图不可用（已回收 / 尺寸非法）
 */
internal fun Bitmap.toArgbPixels(targetLongEdge: Int): IntArray? {
    if (isRecycled || width <= 0 || height <= 0) return null
    val longEdge = maxOf(width, height)
    val effective = if (targetLongEdge <= 0) longEdge else targetLongEdge
    val scaled = if (longEdge <= effective) {
        this
    } else {
        val ratio = effective.toDouble() / longEdge
        Bitmap.createScaledBitmap(
            this,
            (width * ratio).toInt().coerceAtLeast(1),
            (height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }
    return try {
        val w = scaled.width
        val h = scaled.height
        val out = IntArray(w * h)
        // Bitmap.getPixels 导出的就是 0xAARRGGBB，与 GifRecorder 的契约一致
        scaled.getPixels(out, 0, w, 0, 0, w, h)
        out
    } finally {
        // 只回收我们自己新建的那张；调用方传入的位图由调用方负责
        if (scaled !== this) scaled.recycle()
    }
}

/**
 * 用 `PixelCopy` 从渲染面抓一帧（Android 的 ExoPlayer / MediaPlayer 路径）。
 *
 * ⚠️ **必须传与渲染面等大的 [surfaceWidth] × [surfaceHeight]**，PixelCopy 不做缩放。
 * 拿到后再由 [toArgbPixels] 缩到目标尺寸。
 *
 * 失败一律返回 null（不抛异常）：常见失败是 `ERROR_SOURCE_NO_DATA` ——
 * 渲染面当前没有可读缓冲（部分设备用硬件叠加层直出视频时如此）。
 * 调用方（`GifRecorder`）会把它如实报成"抓帧失败 N/M"，而不是产出黑帧。
 */
internal suspend fun pixelCopyArgb(
    source: Surface,
    surfaceWidth: Int,
    surfaceHeight: Int,
    targetLongEdge: Int,
): IntArray? {
    if (surfaceWidth <= 0 || surfaceHeight <= 0 || !source.isValid) return null
    val bitmap = runCatching {
        Bitmap.createBitmap(surfaceWidth, surfaceHeight, Bitmap.Config.ARGB_8888)
    }.getOrNull() ?: return null

    val copied = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            runCatching {
                PixelCopy.request(
                    /* source = */ source,
                    /* bitmap = */ bitmap,
                    /* listener = */ { result ->
                        if (result == PixelCopy.SUCCESS) {
                            cont.resume(true)
                        } else {
                            LogUtil.w(TAG, "PixelCopy 失败，result=$result")
                            cont.resume(false)
                        }
                    },
                    /* handler = */ Handler(Looper.getMainLooper()),
                )
            }.onFailure {
                LogUtil.w(TAG, "PixelCopy.request 抛异常", it)
                cont.resume(false)
            }
        }
    }

    return try {
        if (!copied) null else bitmap.toArgbPixels(targetLongEdge)
    } finally {
        bitmap.recycle()
    }
}

private const val TAG = "AndroidFrameCapture"
