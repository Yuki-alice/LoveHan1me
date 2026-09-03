package io.github.daisukikaffuchino.han1meviewer.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object HanimeDefaults {
    object Spacing {
        val extraSmall = 2.dp
        val small = 4.dp
        val medium = 8.dp
        val large = 12.dp
        val extraLarge = 16.dp
        val itemHorizontal = 24.dp
        val itemVertical = 16.dp
        val contentHorizontal = extraLarge
        val contentVertical = medium
    }

    object Corners {
        val medium: CornerBasedShape
            @Composable get() = MaterialTheme.shapes.medium

        val large: CornerBasedShape
            @Composable get() = MaterialTheme.shapes.largeIncreased
    }

    object Colors {
        val pageSurface: Color
            @Composable get() = MaterialTheme.colorScheme.surfaceContainer

        val card: Color
            @Composable get() = MaterialTheme.colorScheme.surfaceBright

        val homeVideoCard: Color
            @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow

    }

    val buttonShape: CornerBasedShape
        @Composable get() = MaterialTheme.shapes.extraSmall

    val pressedShape: CornerBasedShape
        @Composable get() = MaterialTheme.shapes.small

    @Composable
    fun shapes() = ButtonDefaults.shapes(
        shape = buttonShape,
        pressedShape = pressedShape,
    )

    @Composable
    fun cardShapes() = ButtonDefaults.shapes(
        shape = Corners.large,
        pressedShape = pressedShape,
    )

    val shapesDefaultAnimationSpec: FiniteAnimationSpec<Float>
        @Composable get() = MaterialTheme.motionScheme.defaultEffectsSpec()
}
