package lovehan1me.feature.danmaku

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * 一条弹幕的文字栅格：描边与填色已经烤进 [image]，画面上只剩一次贴图。
 *
 * [paddingPx] 是四周留出的空白 —— 描边有一半落在字形轮廓之外，不留就被裁掉。
 * 贴图时左上角要往回退这么多（[DanmakuLayer] 负责）。
 */
internal class DanmakuRaster(val image: ImageBitmap, val paddingPx: Float)

/** 栅格键：同样文字配同样颜色和字号，画出来必然逐像素相同，可以共用一份位图。 */
internal data class DanmakuRasterKey(val text: String, val argb: Int, val fontSizeSp: Int)

/**
 * 弹幕文字的栅格缓存。
 *
 * ## 为什么要有它
 * 弹幕要"白字黑边"才压得住亮场景，而描边必须单独一遍落笔（`Stroke` 与填色是两个
 * 画笔状态）。同一条弹幕在屏上停留几秒 = 上百帧，等于同一段文字被排版 + 两遍落笔
 * 上百次。把它烤成一张位图后，稳态每帧只剩一次 `drawImage`。
 *
 * ## 容量按像素算而不是按条数
 * 一条长弹幕的位图可以是短句的十几倍（宽度随字数线性涨），按条数封顶会让内存随
 * 场上文本长度失控。这里累积像素数，超预算就从最久未用的开始丢。
 *
 * ## 淘汰只是丢掉引用
 * `ImageBitmap` 背后的像素在 skia 侧由 cleaner 释放，缓存不持有"必须手动关"的句柄，
 * 所以淘汰 = 从表里移除即可，不需要在这里做资源回收。
 */
internal class DanmakuRasterCache(
    private val maxPixels: Long = DEFAULT_MAX_PIXELS,
) {

    private val entries = LinkedHashMap<DanmakuRasterKey, DanmakuRaster>()
    private var totalPixels = 0L

    fun get(key: DanmakuRasterKey, create: () -> DanmakuRaster): DanmakuRaster {
        // 命中即挪到表尾：迭代序里表头永远是最久未用的那个
        entries.remove(key)?.let {
            entries[key] = it
            return it
        }
        val raster = create()
        entries[key] = raster
        totalPixels += raster.image.width.toLong() * raster.image.height
        while (totalPixels > maxPixels && entries.size > 1) {
            val eldest = entries.keys.first()
            val dropped = entries.remove(eldest) ?: break
            totalPixels -= dropped.image.width.toLong() * dropped.image.height
        }
        return raster
    }

    private companion object {
        /** 约 12MB @ 4 字节/像素，与文本测量缓存的量级相当。 */
        const val DEFAULT_MAX_PIXELS = 3_000_000L
    }
}

/**
 * 把一条弹幕烤成位图：一次排版 + 一遍描边 + 一遍填色，之后不再重做。
 *
 * 位图尺寸按**排版结果**给，不是按引擎的碰撞宽度 —— 引擎只关心水平占位，
 * 这里还要多留描边余量和行高（descender 落在外框之内，否则下沿被裁）。
 */
internal fun rasterizeDanmaku(
    text: String,
    argb: Int,
    style: TextStyle,
    textMeasurer: TextMeasurer,
    density: Density,
): DanmakuRaster {
    val layout = textMeasurer.measure(AnnotatedString(text), style)
    val strokePx = with(density) { STROKE_WIDTH.toPx() }
    val padding = strokePx / 2f + 1f
    val width = ceil(layout.size.width + padding * 2f).toInt().coerceAtLeast(1)
    val height = ceil(layout.size.height + padding * 2f).toInt().coerceAtLeast(1)

    val image = ImageBitmap(width, height)
    CanvasDrawScope().draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(image),
        size = Size(width.toFloat(), height.toFloat()),
    ) {
        val topLeft = Offset(padding, padding)
        drawText(
            textLayoutResult = layout,
            color = Color.Black,
            topLeft = topLeft,
            alpha = OUTLINE_ALPHA,
            drawStyle = Stroke(width = strokePx),
        )
        val fill = argbToColor(argb)
        drawText(
            textLayoutResult = layout,
            color = fill,
            topLeft = topLeft,
            alpha = fill.alpha,
        )
    }
    // 位图刚落笔还是 CPU 侧像素，先上传一次，别把这次上传摊到首帧的绘制里
    image.prepareToDraw()
    return DanmakuRaster(image, padding)
}

/** 弹幕颜色是 0xAARRGGBB 的 Int；`Color(Int)` 只吃 24 位 RGB，直接传会丢掉 alpha。 */
internal fun argbToColor(argb: Int): Color =
    Color(((argb.toLong() and 0xFFFFFFFFL) shl 32).toULong())

/** 描边透明度：压得住亮场景又不至于把字糊成一团。 */
private const val OUTLINE_ALPHA = 0.6f

/** 描边宽度用 Dp（Android 上随密度缩放、桌面端 1:1）：1.5dp 在手机上约 3px。 */
private val STROKE_WIDTH = 1.5.dp