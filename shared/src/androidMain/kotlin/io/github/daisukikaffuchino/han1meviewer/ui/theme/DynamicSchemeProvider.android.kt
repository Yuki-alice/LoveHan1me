package io.github.daisukikaffuchino.han1meviewer.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import com.kyant.m3color.dynamiccolor.ColorSpec
import com.kyant.m3color.dynamiccolor.DynamicScheme
import com.kyant.m3color.hct.Hct
import com.kyant.m3color.scheme.SchemeContent
import com.kyant.m3color.scheme.SchemeExpressive
import com.kyant.m3color.scheme.SchemeFidelity
import com.kyant.m3color.scheme.SchemeFruitSalad
import com.kyant.m3color.scheme.SchemeNeutral
import com.kyant.m3color.scheme.SchemeRainbow
import com.kyant.m3color.scheme.SchemeTonalSpot
import com.kyant.m3color.scheme.SchemeVibrant
import io.github.daisukikaffuchino.han1meviewer.logic.model.PaletteStyle

// Android：Theme.kt 原 expressiveColorScheme 逻辑原样搬入（含 @OptIn 由调用方承担）。
@Composable
internal actual fun provideDynamicColorScheme(
    keyColorArgb: Int,
    isDark: Boolean,
    style: PaletteStyle,
    contrastLevel: Double,
): ColorScheme? {
    val scheme = remember(keyColorArgb, isDark, style, contrastLevel) {
        val hct = Hct.fromInt(keyColorArgb)
        val specVersion = ColorSpec.SpecVersion.SPEC_2026
        val platform = DynamicScheme.Platform.PHONE
        when (style) {
            PaletteStyle.TonalSpot -> SchemeTonalSpot(hct, isDark, contrastLevel, specVersion, platform)
            PaletteStyle.Neutral -> SchemeNeutral(hct, isDark, contrastLevel, specVersion, platform)
            PaletteStyle.Vibrant -> SchemeVibrant(hct, isDark, contrastLevel, specVersion, platform)
            PaletteStyle.Expressive -> SchemeExpressive(hct, isDark, contrastLevel, specVersion, platform)
            PaletteStyle.Rainbow -> SchemeRainbow(hct, isDark, contrastLevel, specVersion, platform)
            PaletteStyle.FruitSalad -> SchemeFruitSalad(hct, isDark, contrastLevel, specVersion, platform)
            PaletteStyle.Fidelity -> SchemeFidelity(hct, isDark, contrastLevel, specVersion, platform)
            PaletteStyle.Content -> SchemeContent(hct, isDark, contrastLevel, specVersion, platform)
        }
    }

    return ColorScheme(
        primary = scheme.primary.toComposeColor().animate(),
        onPrimary = scheme.onPrimary.toComposeColor().animate(),
        primaryContainer = scheme.primaryContainer.toComposeColor().animate(),
        onPrimaryContainer = scheme.onPrimaryContainer.toComposeColor().animate(),
        inversePrimary = scheme.inversePrimary.toComposeColor().animate(),
        secondary = scheme.secondary.toComposeColor().animate(),
        onSecondary = scheme.onSecondary.toComposeColor().animate(),
        secondaryContainer = scheme.secondaryContainer.toComposeColor().animate(),
        onSecondaryContainer = scheme.onSecondaryContainer.toComposeColor().animate(),
        tertiary = scheme.tertiary.toComposeColor().animate(),
        onTertiary = scheme.onTertiary.toComposeColor().animate(),
        tertiaryContainer = scheme.tertiaryContainer.toComposeColor().animate(),
        onTertiaryContainer = scheme.onTertiaryContainer.toComposeColor().animate(),
        background = scheme.background.toComposeColor().animate(),
        onBackground = scheme.onBackground.toComposeColor().animate(),
        surface = scheme.surface.toComposeColor().animate(),
        onSurface = scheme.onSurface.toComposeColor().animate(),
        surfaceVariant = scheme.surfaceVariant.toComposeColor().animate(),
        onSurfaceVariant = scheme.onSurfaceVariant.toComposeColor().animate(),
        surfaceTint = scheme.surfaceTint.toComposeColor().animate(),
        inverseSurface = scheme.inverseSurface.toComposeColor().animate(),
        inverseOnSurface = scheme.inverseOnSurface.toComposeColor().animate(),
        error = scheme.error.toComposeColor().animate(),
        onError = scheme.onError.toComposeColor().animate(),
        errorContainer = scheme.errorContainer.toComposeColor().animate(),
        onErrorContainer = scheme.onErrorContainer.toComposeColor().animate(),
        outline = scheme.outline.toComposeColor().animate(),
        outlineVariant = scheme.outlineVariant.toComposeColor().animate(),
        scrim = scheme.scrim.toComposeColor().animate(),
        surfaceBright = scheme.surfaceBright.toComposeColor().animate(),
        surfaceDim = scheme.surfaceDim.toComposeColor().animate(),
        surfaceContainer = scheme.surfaceContainer.toComposeColor().animate(),
        surfaceContainerHigh = scheme.surfaceContainerHigh.toComposeColor().animate(),
        surfaceContainerHighest = scheme.surfaceContainerHighest.toComposeColor().animate(),
        surfaceContainerLow = scheme.surfaceContainerLow.toComposeColor().animate(),
        surfaceContainerLowest = scheme.surfaceContainerLowest.toComposeColor().animate(),
        primaryFixed = scheme.primaryFixed.toComposeColor().animate(),
        primaryFixedDim = scheme.primaryFixedDim.toComposeColor().animate(),
        onPrimaryFixed = scheme.onPrimaryFixed.toComposeColor().animate(),
        onPrimaryFixedVariant = scheme.onPrimaryFixedVariant.toComposeColor().animate(),
        secondaryFixed = scheme.secondaryFixed.toComposeColor().animate(),
        secondaryFixedDim = scheme.secondaryFixedDim.toComposeColor().animate(),
        onSecondaryFixed = scheme.onSecondaryFixed.toComposeColor().animate(),
        onSecondaryFixedVariant = scheme.onSecondaryFixedVariant.toComposeColor().animate(),
        tertiaryFixed = scheme.tertiaryFixed.toComposeColor().animate(),
        tertiaryFixedDim = scheme.tertiaryFixedDim.toComposeColor().animate(),
        onTertiaryFixed = scheme.onTertiaryFixed.toComposeColor().animate(),
        onTertiaryFixedVariant = scheme.onTertiaryFixedVariant.toComposeColor().animate(),
    )
}

@Composable
internal actual fun rememberSystemAccentColorOrNull(): Color? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        colorResource(android.R.color.system_accent1_500)
    } else {
        null
    }
}

@Composable
internal actual fun ConfigureSystemBars(
    colorScheme: ColorScheme,
    isDark: Boolean,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                window.setBackgroundDrawable(colorScheme.surfaceContainer.toArgb().toDrawable())
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !isDark
                    isAppearanceLightNavigationBars = !isDark
                }
            }
        }
    }
}

private fun Int.toComposeColor(): Color = Color(this)

@Composable
private fun Color.animate(animationSpec: AnimationSpec<Color> = spring()): Color =
    animateColorAsState(this, animationSpec, label = "theme-color").value

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
