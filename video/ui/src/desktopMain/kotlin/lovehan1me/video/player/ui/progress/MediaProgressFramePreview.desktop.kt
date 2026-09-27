/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.progress

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.openani.mediamp.features.PreviewFrame

internal actual fun PreviewFrame.toImageBitmap(): ImageBitmap {
    // BGRA_8888 little-endian byte order: b, g, r, a
    val bytes = ByteArray(width * height * 4)
    var i = 0
    for (argb in pixels) {
        bytes[i] = (argb and 0xFF).toByte() // b
        bytes[i + 1] = ((argb shr 8) and 0xFF).toByte() // g
        bytes[i + 2] = ((argb shr 16) and 0xFF).toByte() // r
        bytes[i + 3] = ((argb shr 24) and 0xFF).toByte() // a
        i += 4
    }
    val bitmap = Bitmap()
    bitmap.installPixels(
        ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL),
        bytes,
        width * 4,
    )
    return bitmap.asComposeImageBitmap()
}
