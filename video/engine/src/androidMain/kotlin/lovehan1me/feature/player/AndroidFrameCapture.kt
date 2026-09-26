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
 * 于是把布局阶段拿到的宽高转交给引擎。历史上这是硬编码只给 `MpvPlaybackEngine`
 * 的（`if (engine is MpvPlaybackEngine)`），后提升为能力接口供多引擎共用；
 * Gate3-P6 砍掉 mpv 内核后，**唯一实现方是 [MediampExoPlaybackEngine]**
 * （超分 `needsUpscale` 要渲染面尺寸）。接口保留：它是"引擎需要知道渲染面多大"
 * 的通用契约，不绑内核。
 */
internal interface AndroidSurfaceSizeAware {
    fun updateSurfaceSize(width: Int, height: Int)
}

/**
 * Bitmap → ARGB `IntArray`（0xAARRGGBB，长度 = 宽×高），并**缩到长边不超过 [targetLongEdge]**。
 *
 * ## ⚠️ 当前无调用方（Gate3-P6 起）—— 有意保留，勿当死代码清理
 * 本文件两个抓帧件（本函数与 [pixelCopyArgb]）原先服务于 `ExoPlaybackEngine` 与
 * `MpvPlaybackEngine` 的 `grabFrameArgb`。Gate3-P5 替掉了 Exo 引擎、P6 砍掉了 mpv 内核，
 * Android 于是暂时没有任何引擎实现抓帧。规划口径是「截图/GIF 属 scope 外，链路保留、
 * 入口先藏」：`supportsFrameCapture()` 默认 false ⇒ UI 自动不出入口，这里留着现成件。
 * 接回来时只差在 mediamp-exo 上实现 `grabFrameArgb` —— 渲染面是它家 `PlayerView`，
 * `PixelCopy` 改走 `request(View, …)` 重载（本函数的 `Surface` 版签名随之调整）。
 *
 * ## 为什么必须在这里缩、而不是交给公共层的 `FrameScaler`
 * 1080p 一帧的 `IntArray` 就是 8 MB，而录制要同时持有几十帧
 * （`GifCapturePolicy` 的内存预算按**输出尺寸**估算）。
 * 平台侧本来就带缩放能力（`Bitmap.createScaledBitmap`），
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
 * 用 `PixelCopy` 从渲染面抓一帧（历史上是 Android 的 ExoPlayer / MediaPlayer 路径，
 * 现状见 [toArgbPixels] 顶部的「当前无调用方」说明：保留待接回）。
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
