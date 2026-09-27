package lovehan1me.feature.danmaku

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import lovehan1me.data.danmaku.DanmakuLocation

// rememberDanmakuRenderOptions 已搬入同包 DanmakuRenderWiring.kt
//（订阅设置流，不能进 :video:contract 契约层）。

/**
 * 弹幕绘制层：整个功能里**唯一碰 Compose 的绘制件**。
 *
 * ## 结构与它为什么长这样
 * - 一个 `Canvas`，位置全部由 [DanmakuEngine] 闭式解算，这里只负责"把此刻的布局落笔"。
 * - 行高与速度基准宽度都**实测**出来（[DANMAKU_STUB_TEXT] 那条占位文本），不由字号乘系数：
 *   车道高度是"一行弹幕到底占多高"，而不同字体、不同平台的行距差别不小，用系数就会
 *   在某一端上下两行贴在一起或者缝得过宽。
 * - 文本先经 `rememberTextMeasurer()` 排版：同一份测量结果也喂给引擎的碰撞判断
 *   （[DanmakuEngine.tick] 的 `measureWidth`），于是"以为多宽"与"画出来多宽"必然一致。
 * - 排版之后**每条文字只烤一次位图**（描边与填色都烤进去，见 [DanmakuRasterCache]）：
 *   白字黑边靠两遍画笔状态，直接落笔的话同一条弹幕在屏上停留的上百帧都要重做这两遍。
 * - 不透明度是**整层**的（`Modifier.alpha`）而不是逐条的：逐条给会让同一条弹幕在叠放的
 *   地方浓淡不一，而半透明的字压不住亮场景。
 * - 帧时刻先过 [FrameTimeSmoother] 再交给引擎。弹幕位置是帧时刻的闭式外推，
 *   真机掉一帧就是帧时刻跳一整帧，那一跳会一比一显到屏上。
 * - 落笔的时刻取 [DanmakuFrameDriver.advance] 的返回值（弹幕时钟），**不取播放位置**：
 *   解算布局与落笔必须是同一个数，否则碰撞结论与画面各说各话；播放位置只负责挑到点的弹幕。
 * - 帧循环节流：屏上有弹幕时追 vsync；空屏时退到 20Hz 省掉无谓重绘。
 * - 空集时**一个 draw 调用都不发**。iOS 的画面层是 `AVPlayerLayer`，上面盖一层全尺寸
 *   半透明 Canvas 会迫使根层透明 —— "不画"不等于"没成本"，所以还要 [clipToBounds]
 *   把绘制严格限制在画面矩形内。
 *
 * 不消费任何手势：`Canvas` 本身不可点击，接线时它又排在手势层**之前**
 * （Z 序在画面之上、手势之下），所以弹幕再密也吃不掉一次触摸。
 */
@Composable
fun DanmakuLayer(
    driver: DanmakuFrameDriver,
    modifier: Modifier = Modifier,
    /** false = 完全不画也不跑帧循环（首帧未渲染、PiP、退出全屏等由调用方决定）。 */
    visible: Boolean = true,
    options: DanmakuRenderOptions = DanmakuRenderOptions(),
    /** 字体族、字距等由调用方的排版给；字号、字重、描边由弹幕自己定。 */
    baseStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    if (!visible) return

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = MEASURE_CACHE_SIZE)
    // 占位文本单独一个测量器：它每次都命中同一份结果，别挤掉真实弹幕的缓存位
    val stubMeasurer = rememberTextMeasurer(cacheSize = 1)
    var frameNanos by remember { mutableLongStateOf(0L) }

    // style 必须 remember：绘制层每帧都要用它测量，现造就是每帧一个 TextStyle
    val fontSize = options.clampedFontSizeSp.sp
    val fillStyle = remember(baseStyle, fontSize) { danmakuFillStyle(baseStyle, fontSize) }
    val borderStyle = remember(fillStyle) { danmakuBorderStyle(fillStyle) }
    // 行高 = 占位文本的排版高度 + 上下各 1dp；基准宽度 = 同一次排版的宽度。
    // 两者必须出自同一份样式，否则"车道多高"与"多快算快"就会按两套口径算。
    val stub = remember(stubMeasurer, fillStyle) {
        stubMeasurer.measure(AnnotatedString(DANMAKU_STUB_TEXT), fillStyle)
    }
    val trackHeightPx =
        (stub.size.height + with(density) { (TRACK_VERTICAL_PADDING_DP * 2).toPx() }).toInt()
    val baseSpeedTextWidthPx = stub.size.width.coerceAtLeast(1).toFloat()

    // 两者都**不是 state**：它们在绘制期被读写，一旦走组合期就是每帧重组
    val smoother = remember { FrameTimeSmoother() }
    // 位图是像素尺寸的，密度一变（拖窗口到另一块屏）旧栅格尺寸就不对了
    val rasters = remember(density.density) { DanmakuRasterCache() }

    LaunchedEffect(driver) {
        while (true) {
            withFrameNanos { frameNanos = it }
            if (!driver.engine.hasActiveSlots()) delay(IDLE_POLL_MILLIS)
        }
    }

    Canvas(modifier.clipToBounds().alpha(options.opacity)) {
        // 第一帧的 frameNanos 还是 0：拿它锚定等于给时钟喂一个假的原点
        if (frameNanos == 0L) return@Canvas

        val viewport = DanmakuViewport(
            widthPx = size.width,
            heightPx = size.height,
            lineHeightPx = trackHeightPx.toFloat(),
            displayAreaRatio = options.displayAreaRatio,
            baseSpeedPxPerSecond = options.baseSpeedPxPerSecond(density.density),
            minLaneGapPx = LANE_GAP_DP.toPx(),
            baseSpeedTextWidthPx = baseSpeedTextWidthPx,
        )
        val danmakuNowMs = driver.advance(smoother.smooth(frameNanos), viewport) { item ->
            // 与栅格化那一次填色排版同一个入口：碰撞宽度与实际画出来的宽度必须同源
            measureOneLine(textMeasurer, item.text, fillStyle).size.width.toFloat()
        }
        if (danmakuNowMs == null) return@Canvas

        val engine = driver.engine
        engine.activeScrollSlots.forEach { slot ->
            drawDanmaku(
                slot = slot,
                rasters = rasters,
                textMeasurer = textMeasurer,
                fillStyle = fillStyle,
                borderStyle = borderStyle,
                fontSizeSp = options.clampedFontSizeSp,
                x = engine.leftEdgeOf(slot, danmakuNowMs, viewport),
                y = engine.topEdgeOf(slot, viewport),
            )
        }
        engine.activeFixedSlots.forEach { slot ->
            val y = if (slot.item.location == DanmakuLocation.BOTTOM) {
                engine.bottomEdgeOf(slot, viewport)
            } else {
                engine.topEdgeOf(slot, viewport)
            }
            drawDanmaku(
                slot = slot,
                rasters = rasters,
                textMeasurer = textMeasurer,
                fillStyle = fillStyle,
                borderStyle = borderStyle,
                fontSizeSp = options.clampedFontSizeSp,
                x = engine.centeredLeftEdgeOf(slot, viewport.widthPx),
                y = y,
            )
        }
    }
}

/** 弹幕字重要比正文重一档：细字在视频上几乎读不出来，而这里没有背景板可依靠。 */
private fun danmakuFillStyle(baseStyle: TextStyle, fontSize: TextUnit): TextStyle =
    baseStyle.merge(
        TextStyle(
            fontSize = fontSize,
            fontWeight = FontWeight.W600,
            color = Color.White,
            // 文本内容一直在换：声明给排版系统，别让字形动画机制来"补间"弹幕
            textMotion = TextMotion.Animated,
        ),
    )

/** 描边样式只是给填色样式挂上 `drawStyle`：字重、字号必须一致，否则两遍排版会错位。 */
private fun danmakuBorderStyle(fillStyle: TextStyle): TextStyle =
    fillStyle.copy(drawStyle = DANMAKU_STROKE)

/**
 * 一条弹幕一次贴图。
 *
 * 描边不是装饰 —— 弹幕要盖在视频上，白字压亮场景等于没写。它已经被烤进位图里
 * （见 [rasterizeDanmaku]），所以这里只剩把位图摆到引擎算出的位置。
 *
 * 左上角要往回退 [DanmakuRaster.bleedPx]：引擎给的是**字形**左上角，
 * 而位图四周留了描边余量，不退就会让整条弹幕往右下偏半个余量宽。
 */
private fun DrawScope.drawDanmaku(
    slot: DanmakuSlot,
    rasters: DanmakuRasterCache,
    textMeasurer: TextMeasurer,
    fillStyle: TextStyle,
    borderStyle: TextStyle,
    fontSizeSp: Int,
    x: Float,
    y: Float,
) {
    val raster = rasters.get(DanmakuRasterKey(slot.item.text, slot.item.color, fontSizeSp)) {
        rasterizeDanmaku(
            text = slot.item.text,
            argb = slot.item.color,
            fillStyle = fillStyle,
            borderStyle = borderStyle,
            textMeasurer = textMeasurer,
            density = this@drawDanmaku,
        )
    }
    drawImage(
        image = raster.image,
        topLeft = Offset(x - raster.bleedPx, y - raster.bleedPx),
    )
}

/**
 * 测量缓存条数：一部片子的弹幕文本种类远超默认值，漏一次就是每帧重排一次。
 * 弹幕是滚动出场的，屏上同时在飞的字句要几十上百条，再加历史余量。
 */
private const val MEASURE_CACHE_SIZE = 3_000

/**
 * 车道高度量的是"一行弹幕占多高"，取这一条纯汉字的排版结果：
 * 汉字方块字最接近弹幕的实际高度，夹拉丁文会让行高在两端平台上偏小或偏大。
 */
private const val DANMAKU_STUB_TEXT = "哈哈哈哈"

/** 上下各留 1dp：字形的下沉部与抗锯齿边缘不能贴到车道边界。 */
private val TRACK_VERTICAL_PADDING_DP = 1.dp

/**
 * 同轨两条弹幕之间的最小水平间隙。
 *
 * 必须是 Dp 而不是裸像素：引擎默认值曾经直接写 `24f`，在 3x density 的手机上只有 8dp，
 * 于是"间隙够了"的判断在手机上比在桌面上宽松三倍，弹幕看着贴着脸飞过。
 */
private val LANE_GAP_DP = 36.dp

/** 空屏时的轮询间隔：屏上没字的时候不必追 vsync。 */
private const val IDLE_POLL_MILLIS = 50L
