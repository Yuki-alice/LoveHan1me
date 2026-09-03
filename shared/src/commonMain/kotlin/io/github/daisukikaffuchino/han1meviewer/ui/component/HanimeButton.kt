@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.daisukikaffuchino.han1meviewer.ui.component

import androidx.compose.runtime.remember
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/** Material buttons with app-wide haptic feedback and expressive pressed-shape transitions. */
@Composable
fun HapticButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    shapes: ButtonShapes = ButtonDefaults.shapes(),
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val haptic = rememberHapticFeedback()
    val resolvedShapes = shape?.let { shapes.copy(shape = it) } ?: shapes
    androidx.compose.material3.Button(
        onClick = {
            haptic()
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shapes = resolvedShapes,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

@Composable
fun HapticTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    shapes: ButtonShapes = ButtonShapes(
        shape = ButtonDefaults.textShape,
        pressedShape = ButtonDefaults.pressedShape,
    ),
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val haptic = rememberHapticFeedback()
    val resolvedShapes = shape?.let { shapes.copy(shape = it) } ?: shapes
    androidx.compose.material3.TextButton(
        onClick = {
            haptic()
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        shapes = resolvedShapes,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}
