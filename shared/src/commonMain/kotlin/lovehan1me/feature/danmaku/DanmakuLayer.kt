package lovehan1me.feature.danmaku

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import lovehan1me.data.SettingsRepository
import lovehan1me.data.danmaku.DanmakuLocation

/**
 * 跟着设置走的弹幕观感。改滑杆要**立刻**反映到正在播放的画面上，所以这里订阅的是流
 * 而不是启动时的一次性快照。
 */
@Composable
fun rememberDanmakuRenderOptions(): DanmakuRenderOptions {
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    return remember(settings) { settings.danmakuRenderOptions() }
}

/**
 * 弹幕绘制层：整个功能里**唯一碰 Compose 的绘制件**。
 *
 * ## 结构与它为什么长这样
 * - 一个 `Canvas`，位置全部由 [DanmakuEngine] 闭式解算，这里只负责"把此刻的布局落笔"。
 * - 文本走 `rememberTextMeasurer()` + `drawText(layoutResult)`：本仓没有跨端文本栅格化
 *   入口，自造 expect/actual 三件套不值得，而 `drawText` 三端都是 GPU 合批。
 *   同一份测量缓存也喂给引擎的碰撞判断（[DanmakuEngine.tick] 的 `measureWidth`），
 *   于是"以为多宽"与"画出来多宽"必然一致。
 * - 帧循环节流：屏上有弹幕时追 vsync；**空屏时退到 20Hz** 省掉无谓重绘。50ms 远小于
 *   引擎的迟到窗口（`lateGraceMs`），不会因此把弹幕丢光。
 * - 空集时**一个 draw 调用都不发**。iOS 的画面层是 `AVPlayerLayer`，上面盖一层全尺寸
 *   半透明 Canvas 会迫使根层透明 —— "不画"不等于"没成本"，所以还要 [clipToBounds]
 *   把绘制严格限制在画面矩形内。
 *
 * 不消费任何手势：`Canvas` 本身不可点击，接线时它又排在手势层**之前**
 * （Z 序在画面之上、手势之下），所以弹幕再密也吃不掉一次触摸。
 */
@Composable
fun DanmakuLayer(
    session: DanmakuSession,
    modifier: Modifier = Modifier,
    /** false = 完全不画也不跑帧循环（首帧未渲染、PiP、退出全屏等由调用方决定）。 */
    visible: Boolean = true,
    options: DanmakuRenderOptions = DanmakuRenderOptions(),
) {
    if (!visible) return

    val textMeasurer = rememberTextMeasurer(cacheSize = MEASURE_CACHE_SIZE)
    var frameNanos by remember { mutableLongStateOf(0L) }
    // style 必须 remember：绘制层每帧都要用它测量，现造就是每帧一个 TextStyle
    val fontSize = options.clampedFontSizeSp.sp
    val style = remember(fontSize) { TextStyle(fontSize = fontSize) }

    LaunchedEffect(session) {
        while (true) {
            withFrameNanos { frameNanos = it }
            if (!session.engine.hasActiveSlots()) delay(IDLE_POLL_MILLIS)
        }
    }

    Canvas(modifier.clipToBounds()) {
        // 第一帧的 frameNanos 还是 0：拿它锚定等于给时钟喂一个假的原点
        if (frameNanos == 0L) return@Canvas

        val lineHeightPx = fontSize.toPx() * LINE_HEIGHT_RATIO
        val viewport = DanmakuViewport(
            widthPx = size.width,
            heightPx = size.height,
            lineHeightPx = lineHeightPx,
            displayAreaRatio = options.displayAreaRatio,
            scrollTraverseMs = options.scrollTraverseMs,
            minLaneGapPx = LANE_GAP_DP.toPx(),
        )
        val positionMs = session.advance(frameNanos, viewport) { item ->
            textMeasurer.measure(AnnotatedString(item.text), style).size.width.toFloat()
        }
        if (positionMs == null) return@Canvas

        val engine = session.engine
        engine.activeScrollSlots.forEach { slot ->
            drawDanmaku(
                slot = slot,
                textMeasurer = textMeasurer,
                style = style,
                x = engine.leftEdgeOf(slot, positionMs, viewport),
                y = engine.topEdgeOf(slot, viewport),
                opacity = options.opacity,
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
                textMeasurer = textMeasurer,
                style = style,
                x = engine.centeredLeftEdgeOf(slot, viewport.widthPx),
                y = y,
                opacity = options.opacity,
            )
        }
    }
}

/**
 * 一条弹幕两遍落笔：先描边再填色。
 *
 * 描边不是装饰 —— 弹幕要盖在视频上，白字压亮场景等于没写。两遍共用同一份
 * `TextLayoutResult`（测量缓存命中），所以多的是 GPU 合批，不是排版。
 */
private fun DrawScope.drawDanmaku(
    slot: DanmakuSlot,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    x: Float,
    y: Float,
    opacity: Float,
) {
    val layout = textMeasurer.measure(AnnotatedString(slot.item.text), style)
    val color = argbToColor(slot.item.color)
    val topLeft = Offset(x, y)
    drawText(
        textLayoutResult = layout,
        color = Color.Black,
        topLeft = topLeft,
        alpha = opacity * OUTLINE_ALPHA,
        drawStyle = Stroke(width = STROKE_WIDTH.toPx()),
    )
    drawText(
        textLayoutResult = layout,
        color = color,
        alpha = opacity * color.alpha,
        topLeft = topLeft,
    )
}

/** 弹幕颜色是 0xAARRGGBB 的 Int；`Color(Int)` 只吃 24 位 RGB，直接传会丢掉 alpha。 */
private fun argbToColor(argb: Int): Color =
    Color(((argb.toLong() and 0xFFFFFFFFL) shl 32).toULong())

/** 测量缓存条数：一部片子的弹幕文本种类远超 100（默认值），漏一次就是每帧重排一次。 */
private const val MEASURE_CACHE_SIZE = 512

/** 行高 = 字号 × 这个系数：留出descender 与行间余量，否则上下两行会贴在一起。 */
private const val LINE_HEIGHT_RATIO = 1.25f
private const val OUTLINE_ALPHA = 0.6f

/**
 * 同轨两条弹幕之间的最小水平间隙。
 *
 * 必须是 Dp 而不是裸像素：引擎默认值曾经直接写 `24f`，在 3x density 的手机上只有 8dp，
 * 于是"间隙够了"的判断在手机上比在桌面上宽松三倍，弹幕看着贴着脸飞过。
 */
private val LANE_GAP_DP = 24.dp

/** 描边宽度用 Dp（Android 上随密度缩放、桌面端 1:1）：1.5dp 在手机上约 3px，够压住亮场景又糊字。 */
private val STROKE_WIDTH = 1.5.dp

/** 空屏时的轮询间隔，同时也是数据节拍：与引擎的 lateGraceMs 同量级且更小。 */
private const val IDLE_POLL_MILLIS = 50L
