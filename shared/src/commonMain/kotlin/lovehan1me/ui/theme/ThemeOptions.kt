package lovehan1me.ui.theme

import androidx.compose.ui.graphics.Color
import lovehan1me.core.domain.model.PaletteStyle
import lovehan1me.core.domain.model.ThemeAccent

typealias ThemeAccentColor = ThemeAccent
typealias AppPaletteStyle = PaletteStyle

val ThemeAccentColor.label: String
    get() = when (this) {
        ThemeAccentColor.Pink -> "Momoi"
        ThemeAccentColor.Green -> "Midori"
        ThemeAccentColor.Yellow -> "Yuzu"
        ThemeAccentColor.Blue -> "Arisu"
    }

val ThemeAccentColor.colors: List<Color>
    get() = when (this) {
        ThemeAccentColor.Pink -> listOf(Color(0xFFF596AA), Color(0xFFFFB1BF), Color(0xFFE46988), Color(0xFFEDBE92))
        ThemeAccentColor.Green -> listOf(Color(0xFF8BC34A), Color(0xFF9FD75C), Color(0xFF6A9F2B), Color(0xFF9FD0CC))
        ThemeAccentColor.Yellow -> listOf(Color(0xFFFFF59D), Color(0xFFD7CA2C), Color(0xFF9E9401), Color(0xFFA6D0BA))
        ThemeAccentColor.Blue -> listOf(Color(0xFF03A9F4), Color(0xFF8ECDFF), Color(0xFF0099DD), Color(0xFFCFC0E7))
    }

val AppPaletteStyle.label: String
    get() = when (this) {
        AppPaletteStyle.TonalSpot -> "Tonal Spot"
        AppPaletteStyle.Neutral -> "Neutral"
        AppPaletteStyle.Vibrant -> "Vibrant"
        AppPaletteStyle.Expressive -> "Expressive"
        AppPaletteStyle.Rainbow -> "Rainbow"
        AppPaletteStyle.FruitSalad -> "Fruit Salad"
        AppPaletteStyle.Fidelity -> "Fidelity"
        AppPaletteStyle.Content -> "Content"
    }
