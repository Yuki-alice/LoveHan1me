package lovehan1me.core.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max

/**
 * 桌面端头像裁剪的像素活。
 *
 * - 解码：`ImageIO` 读文件后转 Compose `ImageBitmap`（经 Skia `Image`）。
 *   降采样用 `Image.getScaledInstance` 会因为色彩模型变化引入偏差，
 *   这里改用 `BufferedImage` + `Graphics2D` 高质量插值。
 * - 裁剪落盘：按矩形 `getSubimage` → 缩放到 outputPx → 写 PNG 到缓存目录。
 */
actual suspend fun decodeAvatarSource(source: String, maxPx: Int): ImageBitmap? =
    runCatching {
        val file = source.removePrefix("file://").let { File(it) }
        if (!file.exists()) return null
        val raw = ImageIO.read(file) ?: return null
        val scale = (maxPx.toFloat() / max(raw.width, raw.height)).coerceAtMost(1f)
        val scaled = if (scale < 1f) {
            val w = (raw.width * scale).toInt().coerceAtLeast(1)
            val h = (raw.height * scale).toInt().coerceAtLeast(1)
            BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).apply {
                createGraphics().apply {
                    setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                    )
                    drawImage(raw, 0, 0, w, h, null)
                    dispose()
                }
            }
        } else {
            raw
        }
        // 走内存：BufferedImage → PNG 字节 → Skia Image → Compose ImageBitmap
        val bos = java.io.ByteArrayOutputStream()
        ImageIO.write(scaled, "png", bos)
        Image.makeFromEncoded(bos.toByteArray()).toComposeImageBitmap()
    }.getOrNull()

actual suspend fun cropAndSaveAvatar(
    source: String,
    rect: AvatarCropRect,
    outputPx: Int,
): String? = runCatching {
    val file = source.removePrefix("file://").let { File(it) }
    if (!file.exists()) return null
    val raw = ImageIO.read(file) ?: return null

    // 裁剪矩形必须落在图像内（共享层已夹取，这里再兜一次）
    val x = rect.x.coerceIn(0, max(0, raw.width - 1))
    val y = rect.y.coerceIn(0, max(0, raw.height - 1))
    val size = minOf(rect.size, raw.width - x, raw.height - y).coerceAtLeast(1)

    val cropped = raw.getSubimage(x, y, size, size)
    val target = BufferedImage(outputPx, outputPx, BufferedImage.TYPE_INT_ARGB).apply {
        createGraphics().apply {
            setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            drawImage(cropped, 0, 0, outputPx, outputPx, null)
            dispose()
        }
    }

    val dir = avatarCacheDir()
    val out = File(dir, "avatar_${System.currentTimeMillis()}.png")
    ImageIO.write(target, "png", out)
    out.absolutePath
}.getOrNull()

private fun avatarCacheDir(): File =
    File(System.getProperty("java.io.tmpdir"), "lovehan1me-avatar").apply { mkdirs() }
