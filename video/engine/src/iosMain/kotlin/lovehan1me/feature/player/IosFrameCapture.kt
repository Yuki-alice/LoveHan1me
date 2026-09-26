@file:OptIn(ExperimentalForeignApi::class)

package lovehan1me.feature.player

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.delay
import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.core.util.LogUtil
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemVideoOutput
import platform.AVFoundation.addOutput
import platform.AVFoundation.currentItem
import platform.AVFoundation.pause
import platform.AVFoundation.removeOutput
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.NSNumber

private const val TAG = "IosFrameCapture"

// ⚠️ 本文件当前无调用方（Gate3-P6 起）—— 有意保留，勿当死代码清理。
// [grabIosFrameArgb] 原先只被 `IosAVPlaybackEngine.grabFrameArgb` 调用，该引擎随
// Gate3-P5 换底（mediamp-avkit）退出、P6 删除。规划口径是「截图/GIF 属 scope 外，
// 链路保留、入口先藏」（`supportsFrameCapture()` 默认 false ⇒ UI 不出入口），
// 故留着这个 iOS 抓帧件；接回来时在 `MediampAvPlaybackEngine` 上实现 `grabFrameArgb` 即可。

/** 等 `AVPlayerItemVideoOutput` 产出该时刻解码帧的上限。 */
private const val OUTPUT_READY_TIMEOUT_MS = 2_000L
private const val POLL_MS = 40L

/**
 * iOS 取帧（M3-b）。
 *
 * ## 走的什么 API
 * `AVPlayerItemVideoOutput` —— iOS 上"从**正在播放的** AVPlayer 取一帧"的正规做法，
 * 与另两端的取向一致（桌面 mediamp `FramePreview`、Android mpv `screenshot-raw` / Exo `PixelCopy`
 * 也都是抓"当前已经渲染/解码出来的那一帧"，而不是重新打开媒体源）。
 *
 * 刻意**不用 `AVAssetImageGenerator`**：它按 asset 重新解码，会绕开播放器当前的解码会话，
 * 与我们"抓播放器此刻这一帧"的语义不符（也与另两端的实现方式不一致）。
 *
 * ## 为什么是"先 seek，再等 output 有新缓冲"
 * `seekToTime` 返回时目标帧往往还没解码出来，直接取会拿到旧帧。
 * `hasNewPixelBufferForItemTime` 是**真正可靠的就绪信号**（比轮询播放位置准），
 * 所以这里的等待以它为准，配合超时。公共层的 [FrameReadyWaiter] 用于桌面/Android 的
 * 位置/seek 判定，iOS 侧由本函数自带的 output 轮询承担同一职责。
 *
 * ## 缩放不在本函数做
 * 目标尺寸在这里其实**是确切已知的**（像素缓冲的真实宽高可直接读到），但 GIF 录制管线
 * 不在本模块，本函数在尺寸不等时**直返源尺寸**，缩放交给调用方 `GifRecorder.scaleToPlan`
 * 补做 —— `PlaybackEngine.grabFrameArgb` 的契约明确允许"只能给源尺寸就返回源尺寸"。
 *
 * 代价：`GifRecorder.scaleToPlan` 只能靠**引擎上报的** `videoWidth/videoHeight`
 * 判断"要不要缩"。而 iOS 的 `readVideoSize()` 走的是 `AVAssetTrack.naturalSize` +
 * `preferredTransform`，**旋转视频下可能与像素缓冲的宽高互换** → 缩放被静默跳过
 * → 最终由编码器的尺寸校验报错（安全但功能不可用）。
 *
 * ## ⚠️ 未在 macOS 上编译验证过
 * Windows 构建不了 Kotlin/Native，本文件只能由 CI 的 macOS runner
 * （`.github/workflows/ci.yml` 的 ios-compile job）编译把关。下面几处是**首次编译最可能报错的地方**，
 * 已在代码里逐条标注，便于一轮修完。
 */
internal suspend fun grabIosFrameArgb(
    player: AVPlayer,
    positionMs: Long,
    targetWidth: Int,
    targetHeight: Int,
): IntArray? {
    val item = player.currentItem ?: return null
    val time = CMTimeMakeWithSeconds(positionMs / 1000.0, 600)

    // ⚠️ 风险点 1：`pixelBufferAttributes` 的桥接。
    // kCVPixelBufferPixelFormatTypeKey 是 CoreVideo 的 CFString 常量，
    // 靠 CFString↔NSString 的 toll-free bridging 作为字典 key（KMP 社区通行写法）。
    // 若 Kotlin/Native 把它暴露成裸 CFStringRef 而无法进 Map，改法是直接用字面量
    // key "PixelFormatType"（该常量的实际取值）。
    val output = AVPlayerItemVideoOutput(
        pixelBufferAttributes = mapOf<Any?, Any?>(
            // ⚠️ 风险点 2：用 int 重载而非 unsignedInt，避开 OSType(UInt32) 的签名差异
            kCVPixelBufferPixelFormatTypeKey to NSNumber(int = kCVPixelFormatType_32BGRA.toInt()),
        ),
    )
    item.addOutput(output)
    try {
        player.pause()
        player.seekToTime(time)

        val deadline = currentEpochMillis() + OUTPUT_READY_TIMEOUT_MS
        while (!output.hasNewPixelBufferForItemTime(time)) {
            if (currentEpochMillis() >= deadline) {
                LogUtil.w(TAG, "抓帧超时：${positionMs}ms 处始终没有新的像素缓冲")
                return null
            }
            delay(POLL_MS)
        }

        // ⚠️ 风险点 3：第二个参数（itemTimeForDisplay）传 null 的绑定形态
        val pixelBuffer = output.copyPixelBufferForItemTime(time, null) ?: return null
        return pixelBuffer.toArgbPixelsScaled(targetWidth, targetHeight)
    } finally {
        item.removeOutput(output)
    }
}

/**
 * `CVPixelBuffer`(32BGRA) → ARGB `IntArray`（0xAARRGGBB），并缩到 [targetWidth] × [targetHeight]。
 *
 * `kCVPixelFormatType_32BGRA` 在内存里的字节序是 **B,G,R,A**，
 * 小端机上打包成 32 位整数正好就是 `0xAARRGGBB`，与 `PlaybackEngine.grabFrameArgb` 的契约一致。
 *
 * 尺寸不等时**不在这里缩**：只做像素格式转换并原样返回源尺寸，缩放由调用方
 * `GifRecorder.scaleToPlan` 补做（面积平均，`:video:ui` 侧）。
 *
 * ⚠️ 风险点 4：`CVPixelBufferLockBaseAddress` 的返回码比较。
 * 这里刻意写 `!= 0` 而不是 `!= kCVReturnSuccess` —— 少依赖一个常量的暴露形态（两者等价，0 即成功）。
 * ⚠️ 风险点 5：`reinterpret<UByteVar>()` 用于把 `COpaquePointer` 当字节数组读。
 */
private fun CVPixelBufferRef.toArgbPixelsScaled(targetWidth: Int, targetHeight: Int): IntArray? {
    if (CVPixelBufferLockBaseAddress(this, kCVPixelBufferLock_ReadOnly) != 0) {
        LogUtil.w(TAG, "CVPixelBufferLockBaseAddress 失败")
        return null
    }
    try {
        val width = CVPixelBufferGetWidth(this).toInt()
        val height = CVPixelBufferGetHeight(this).toInt()
        val stride = CVPixelBufferGetBytesPerRow(this).toInt()
        if (width <= 0 || height <= 0 || stride <= 0) return null
        val base = CVPixelBufferGetBaseAddress(this) ?: return null
        // 指针算术在 cinterop 里形态不稳，整块一次读出再按行跨步索引：
        // 每行 width*4 有效字节，行与行之间隔 stride（含系统填充），Byte 转 Int 去符号扩展。
        val baseBytes = base.reinterpret<ByteVar>()
        val totalBytes = stride * height
        if (totalBytes <= 0) return null
        val all = baseBytes.readBytes(totalBytes)
        if (all.size < totalBytes) return null
        val source = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val o = y * stride + x * 4
                val b = all[o].toInt() and 0xFF
                val g = all[o + 1].toInt() and 0xFF
                val r = all[o + 2].toInt() and 0xFF
                val a = all[o + 3].toInt() and 0xFF
                source[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        if (width == targetWidth && height == targetHeight) return source
        // 源尺寸直返：缩放由调用方（GifRecorder）补做——grabFrameArgb 契约明确允许
        // "只能给源尺寸就返回源尺寸"；:video:engine 不得反向依赖 GIF 管线所在模块。
        return source
    } finally {
        CVPixelBufferUnlockBaseAddress(this, kCVPixelBufferLock_ReadOnly)
    }
}
