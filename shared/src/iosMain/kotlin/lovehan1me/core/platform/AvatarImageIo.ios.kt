package lovehan1me.core.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.IRect
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * 阶段一⑧：iOS 头像裁剪的像素活 —— **纯 Kotlin（Skia）+ 既有 Foundation 模式**，
 * 不引入新的 UIKit cinterop（图片*选择器*仍需后续接入，见下）。
 *
 * API 签名已对同版本 skiko（0.150.1）javap 核对：
 * `Bitmap.extractSubset` / `Image.makeFromBitmap` / `Image.encodeToData` / `Data.getBytes`。
 *
 * 与桌面实现的差异：
 * - 不做 maxPx 降采样（skia 缩放需走 Canvas，留待真机验证后再加）
 * - 不缩放裁剪产物到 outputPx：直接按源分辨率输出正方形（展示端自会缩放）
 * - source 只支持**本地路径**（沙盒内）。iOS 的图片选择器尚未接入，故该管线
 *   当前尚无入口——接入后此处即可用。
 */
private fun documentsAvatarDir(): String {
    val documents = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true,
    ).first() as String
    return "$documents/Han1meViewer/avatar"
}

@OptIn(ExperimentalForeignApi::class)
private fun ensureDir(path: String) {
    NSFileManager.defaultManager.createDirectoryAtPath(path, true, null, null)
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    val ptr = bytes
    if (out.isNotEmpty() && ptr != null) {
        out.usePinned { memcpy(it.addressOf(0), ptr, length) }
    }
    return out
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = size.toULong())
}

internal fun readBytesAtPath(path: String): ByteArray? =
    runCatching { NSData.dataWithContentsOfFile(path)?.toByteArray() }.getOrNull()

internal fun writeBytesAtPath(path: String, bytes: ByteArray): Boolean =
    runCatching { bytes.toNSData().writeToFile(path, atomically = true) }.getOrDefault(false)

actual suspend fun decodeAvatarSource(source: String, maxPx: Int): ImageBitmap? =
    withContext(Dispatchers.Default) {
        runCatching {
            val bytes = readBytesAtPath(source) ?: return@withContext null
            Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull()
    }

actual suspend fun cropAndSaveAvatar(
    source: String,
    rect: AvatarCropRect,
    outputPx: Int,
): String? = withContext(Dispatchers.Default) {
    runCatching {
        val bytes = readBytesAtPath(source) ?: return@withContext null
        val full = Image.makeFromEncoded(bytes).toComposeImageBitmap().asSkiaBitmap()

        // 裁剪矩形夹回图内（共享层已夹取，这里再兜一次）
        val x = rect.x.coerceIn(0, maxOf(0, full.width - 1))
        val y = rect.y.coerceIn(0, maxOf(0, full.height - 1))
        val size = minOf(rect.size, full.width - x, full.height - y).coerceAtLeast(1)

        val subset = Bitmap()
        check(full.extractSubset(subset, IRect.makeXYWH(x, y, size, size))) {
            "extractSubset failed: rect=($x,$y,$size) src=${full.width}x${full.height}"
        }

        val data = Image.makeFromBitmap(subset).encodeToData(EncodedImageFormat.PNG)
        val dir = documentsAvatarDir()
        ensureDir(dir)
        val out = "$dir/avatar_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}.png"
        if (!writeBytesAtPath(out, data.getBytes())) return@withContext null
        out
    }.onFailure {
        lovehan1me.core.util.LogUtil.w("AvatarCropIos", "裁剪失败", it)
    }.getOrNull()
}
