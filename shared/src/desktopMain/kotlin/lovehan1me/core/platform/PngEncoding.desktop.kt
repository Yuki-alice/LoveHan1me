package lovehan1me.core.platform

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * 桌面：`BufferedImage(TYPE_INT_ARGB)` + `ImageIO.write("png")`。
 *
 * `TYPE_INT_ARGB` 的内存布局就是"非预乘的 32 位 0xAARRGGBB"，与 `grabFrameArgb`
 * 的契约逐位一致，`setRGB` 是整行批量写入（无逐像素换算）。
 * 与 `AvatarImageIo.desktop.kt` 用的是同一套 JVM 图像 API，无新增依赖。
 */
actual fun encodePngArgb(pixels: IntArray, width: Int, height: Int): ByteArray? {
    if (width <= 0 || height <= 0 || pixels.size != width * height) return null
    return runCatching {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, width, height, pixels, 0, width)
        ByteArrayOutputStream().use { out ->
            if (ImageIO.write(image, "png", out)) out.toByteArray() else null
        }
    }.getOrNull()
}
