package lovehan1me.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 形状阶梯 —— 显式声明（审计 P1 收敛后，全项目圆角唯一入口）。
 *
 * 数值与 M3 1.5.0-alpha25 的 `Shapes()` 默认值一一对应（4/8/12/16/20/28/32/48），
 * 视觉零变化；显式写出的目的是给未来调整一个唯一落点，并让"项目用哪套圆角"在代码里可读。
 *
 * 散装 `RoundedCornerShape(n)` 的收敛映射（±2dp 的就近归档已记录在审计报告 §三 P1）：
 * 2/4 → extraSmall，6/8 → small，10/12 → medium，14/16 → large，
 * 18/20 → largeIncreased，28 → extraLarge，32/36 → extraLargeIncreased。
 * 胶囊（50/100/999/percent=50）不在此处 —— 走 [HanimeDefaults.Corners.pill]。
 */
val AppShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    largeIncreased = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun shapeByInteraction(
    shapes: ButtonShapes,
    pressed: Boolean,
    animationSpec: FiniteAnimationSpec<Float>,
): Shape {
    val normal = shapes.shape
    val pressedShape = shapes.pressedShape
    if (normal !is CornerBasedShape || pressedShape !is CornerBasedShape) {
        return if (pressed) pressedShape else normal
    }

    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = animationSpec,
        label = "interactive-shape-progress",
    )
    return remember(normal, pressedShape, progress) {
        InterpolatedCornerShape(normal, pressedShape, progress)
    }
}

private class InterpolatedCornerShape(
    private val start: CornerBasedShape,
    private val end: CornerBasedShape,
    private val progress: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        fun radius(startPx: Float, endPx: Float): CornerRadius {
            val value = startPx + (endPx - startPx) * progress
            return CornerRadius(value.coerceIn(0f, size.minDimension / 2f))
        }

        val topStart = radius(
            start.topStart.toPx(size, density),
            end.topStart.toPx(size, density),
        )
        val topEnd = radius(
            start.topEnd.toPx(size, density),
            end.topEnd.toPx(size, density),
        )
        val bottomStart = radius(
            start.bottomStart.toPx(size, density),
            end.bottomStart.toPx(size, density),
        )
        val bottomEnd = radius(
            start.bottomEnd.toPx(size, density),
            end.bottomEnd.toPx(size, density),
        )
        val leftToRight = layoutDirection == LayoutDirection.Ltr
        return Outline.Rounded(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                topLeftCornerRadius = if (leftToRight) topStart else topEnd,
                topRightCornerRadius = if (leftToRight) topEnd else topStart,
                bottomRightCornerRadius = if (leftToRight) bottomEnd else bottomStart,
                bottomLeftCornerRadius = if (leftToRight) bottomStart else bottomEnd,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun animatedShape(
    shapes: ButtonShapes,
    interactionSource: MutableInteractionSource? = null,
): Shape {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return shapeByInteraction(
        shapes = shapes,
        pressed = pressed,
        animationSpec = HanimeDefaults.shapesDefaultAnimationSpec,
    )
}
