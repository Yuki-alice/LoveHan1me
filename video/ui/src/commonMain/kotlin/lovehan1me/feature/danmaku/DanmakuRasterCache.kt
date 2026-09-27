package lovehan1me.feature.danmaku

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.max

/**
 * 一条弹幕的文字栅格：描边与填色已经烤进 [image]，画面上只剩一次贴图。
 *
 * [bleedPx] 是四周留出的空白：描边有半个宽度落在字形轮廓之外，行高里也可能容不下
 * 下沉部的墨。贴图时左上角要往回退这么多（[DanmakuLayer] 负责）。
 */
internal class DanmakuRaster(val image: ImageBitmap, val bleedPx: Float)

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
 * 把一条弹幕烤成位图：两遍排版（填色 / 描边各一次）+ 两遍落笔，之后不再重做。
 *
 * 位图尺寸取**两次排版结果的较大值**再四周加一圈余量。描边必须单独排一次版：
 * `TextStyle.drawStyle` 会参与尺寸计算，只按填色那次排版给尺寸就会把描边切掉。
 * 留白取行高的一半 —— 比"半个描边宽"更宽，为的是把下沉部和抗锯齿边缘也收进来。
 */
internal fun rasterizeDanmaku(
    text: String,
    argb: Int,
    fillStyle: TextStyle,
    borderStyle: TextStyle,
    textMeasurer: TextMeasurer,
    density: Density,
): DanmakuRaster {
    val solid = measureOneLine(textMeasurer, text, fillStyle)
    val border = measureOneLine(textMeasurer, text, borderStyle)
    val width = max(border.size.width, solid.size.width).coerceAtLeast(1)
    val height = max(border.size.height, solid.size.height).coerceAtLeast(1)
    val bleed = (height / 2).toFloat()

    val image = ImageBitmap(width + bleed.toInt() * 2, height + bleed.toInt() * 2)
    CanvasDrawScope().draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(image),
        size = Size(image.width.toFloat(), image.height.toFloat()),
    ) {
        val topLeft = Offset(bleed, bleed)
        // 描边先落，填色压在它上面：反过来会把描边咬掉一圈
        drawText(
            textLayoutResult = border,
            color = DANMAKU_STROKE_COLOR,
            topLeft = topLeft,
            drawStyle = DANMAKU_STROKE,
        )
        drawText(
            textLayoutResult = solid,
            color = danmakuFillColor(argb),
            topLeft = topLeft,
        )
    }
    // 位图刚落笔还是 CPU 侧像素，先上传一次，别把这次上传摊到首帧的绘制里
    image.prepareToDraw()
    return DanmakuRaster(image, bleed)
}

/** 单行、不换行、超出就裁：弹幕没有"折行"这回事，多行会把行高与碰撞宽度全带偏。 */
internal fun measureOneLine(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
): TextLayoutResult = textMeasurer.measure(
    text = AnnotatedString(text),
    style = style,
    overflow = TextOverflow.Clip,
    maxLines = 1,
    softWrap = false,
)

/**
 * 弹幕颜色是 0xAARRGGBB 的 Int，但**透明度不取**：整层的透明度由 `Modifier.alpha` 统一给
 * （见 [DanmakuLayer]）。逐条各带 alpha 会让同一条弹幕在不同帧、不同叠放下浓淡不一，
 * 而半透明的字压不住亮场景。
 */
internal fun danmakuFillColor(argb: Int): Color =
    Color(DANMAKU_OPAQUE_ALPHA or (argb.toUInt().toLong()))

/** 填色通道的 alpha 固定为不透明。 */
private const val DANMAKU_OPAQUE_ALPHA = 0xFF000000L

/**
 * 描边宽度是**裸像素**不是 dp：弹幕盖在视频上，观感要的是"一圈黑边"，让它随屏幕密度
 * 变化会在高 dpi 屏上糊成一团、在桌面上细得看不见。4px 与字号 18sp 是配好的。
 *
 * `miter` 与 `Round` 一起决定尖角怎么接：斜接在撇捺上会伸出很长的尖刺。
 * 填色样式也挂同一个 drawStyle（见 `danmakuBorderStyle`），两遍排版才不会错位。
 */
internal val DANMAKU_STROKE = Stroke(
    miter = 3f,
    width = DANMAKU_STROKE_WIDTH_PX,
    join = StrokeJoin.Round,
)

private const val DANMAKU_STROKE_WIDTH_PX = 4f

private val DANMAKU_STROKE_COLOR = Color.Black
