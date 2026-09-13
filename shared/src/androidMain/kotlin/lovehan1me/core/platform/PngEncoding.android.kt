package lovehan1me.core.platform

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * Android：`Bitmap.createBitmap(IntArray, ...)` + `Bitmap.compress(PNG)`。
 *
 * 走系统编码器而不是自研：PNG 的 deflate 层交给平台，体积与画质都是最优，
 * 也避免往 APK 里塞一块纯 Kotlin 的压缩实现。
 *
 * `createBitmap` 收的就是 `0xAARRGGBB`（与 `grabFrameArgb` 的契约逐位一致），
 * 不需要逐像素换算；`compress` 的 quality 对 PNG 无意义（无损），传 100 只是惯例。
 */
actual fun encodePngArgb(pixels: IntArray, width: Int, height: Int): ByteArray? {
    if (width <= 0 || height <= 0 || pixels.size != width * height) return null
    return runCatching {
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        try {
            ByteArrayOutputStream().use { out ->
                if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) out.toByteArray() else null
            }
        } finally {
            bitmap.recycle()
        }
    }.getOrNull()
}
