package lovehan1me.feature.danmaku

import lovehan1me.core.domain.model.AppSettings

/**
 * 弹幕观感的四个可调量（全部来自设置页，区间见下面的常量）。
 *
 * 存的是**百分比与 sp**而不是比例和毫秒：设置项要显示成"80%""1.0×"这种用户能核对的数，
 * 而折算规则（尤其速度那条是反比）只该有一处实现 —— 就是本类的派生属性。
 *
 * 这个类型刻意不碰 Compose：设置页要读同一批区间来摆滑杆，
 * 让常量住在绘制件里就等于逼设置页去 import 一个 `Canvas`。
 */
data class DanmakuRenderOptions(
    val fontSizeSp: Int = DANMAKU_DEFAULT_FONT_SIZE_SP,
    val opacityPercent: Int = DANMAKU_DEFAULT_OPACITY_PERCENT,
    val displayAreaPercent: Int = DANMAKU_DEFAULT_DISPLAY_AREA_PERCENT,
    val speedPercent: Int = DANMAKU_DEFAULT_SPEED_PERCENT,
) {
    /** 盘上的值可能是任何历史版本写下的，进引擎前一律夹回区间。 */
    val clampedFontSizeSp: Int get() = fontSizeSp.coerceIn(DANMAKU_FONT_SIZE_RANGE)
    val opacity: Float get() = opacityPercent.clampedPercent(DANMAKU_OPACITY_RANGE)
    val displayAreaRatio: Float get() = displayAreaPercent.clampedPercent(DANMAKU_DISPLAY_AREA_RANGE)
    val scrollTraverseMs: Long get() = danmakuTraverseMs(speedPercent)
}

/** 默认值与 [AppSettings] 里的弹幕默认值一致：两处不一致时，滑杆初始位置会骗人。 */
const val DANMAKU_DEFAULT_FONT_SIZE_SP = 18
const val DANMAKU_DEFAULT_OPACITY_PERCENT = 80
const val DANMAKU_DEFAULT_DISPLAY_AREA_PERCENT = 50
const val DANMAKU_DEFAULT_SPEED_PERCENT = 100

/** 设置页滑杆与绘制层**共用**这些区间：分头写就会出现"能拖到的值被绘制层偷偷夹掉"。 */
val DANMAKU_FONT_SIZE_RANGE = 12..30
val DANMAKU_OPACITY_RANGE = 30..100
val DANMAKU_DISPLAY_AREA_RANGE = 25..100
val DANMAKU_SPEED_RANGE = DANMAKU_SPEED_MIN_PERCENT..DANMAKU_SPEED_MAX_PERCENT

fun AppSettings.danmakuRenderOptions(): DanmakuRenderOptions = DanmakuRenderOptions(
    fontSizeSp = danmakuFontSizeSp,
    opacityPercent = danmakuOpacityPercent,
    displayAreaPercent = danmakuDisplayAreaPercent,
    speedPercent = danmakuSpeedPercent,
)

private fun Int.clampedPercent(range: IntRange): Float = coerceIn(range).toFloat() / 100f
