@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)

package me.lovehan1me.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import me.lovehan1me.ui.theme.HanimeDefaults
import me.lovehan1me.ui.theme.shapeByInteraction

/** Shared plain container surface used by cards and settings items. */
@Composable
fun CardContainerSurface(
    modifier: Modifier = Modifier,
    shape: Shape = HanimeDefaults.Corners.large,
    color: Color? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color ?: HanimeDefaults.Colors.card,
        content = content,
    )
}

/** Clickable variant with the same app-wide card styling. */
@Composable
fun CardContainerSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shapes: ButtonShapes = HanimeDefaults.cardShapes(),
    color: Color? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val haptic = rememberHapticFeedback()
    val animatedShape = shapeByInteraction(
        shapes = shapes,
        pressed = source.collectIsPressedAsState().value,
        animationSpec = HanimeDefaults.shapesDefaultAnimationSpec,
    )
    Surface(
        modifier = modifier,
        shape = animatedShape,
        color = color ?: HanimeDefaults.Colors.card,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.combinedClickable(
                enabled = enabled,
                interactionSource = source,
                indication = indication,
                onClick = {
                    haptic()
                    onClick()
                },
                onLongClick = {},
            ),
        ) {
            content()
        }
    }
}
