@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package lovehan1me.feature.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.video_landscape_layout_style_summary
import lovehan1me.video_landscape_layout_style
import lovehan1me.preset_color_scheme_summary
import lovehan1me.preset_color_scheme
import lovehan1me.palette_style_summary
import lovehan1me.palette_style
import lovehan1me.layout_style_dual_pane
import lovehan1me.layout_style_classic
import lovehan1me.follow_system
import lovehan1me.dark_theme
import lovehan1me.dark_mode_picker_summary
import lovehan1me.always_on
import lovehan1me.always_off
import lovehan1me.ic_lightbulb
import lovehan1me.ic_light_mode
import lovehan1me.ic_dark_mode
import lovehan1me.bg_settings_pad_new
import lovehan1me.bg_settings_pad_classic
import lovehan1me.ui.component.immediateClickable
import lovehan1me.ui.theme.AppPaletteStyle
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.theme.ThemeAccentColor
import lovehan1me.ui.theme.colors
import lovehan1me.ui.theme.label
import lovehan1me.ui.theme.animatedShape
import lovehan1me.ui.theme.expressiveColorScheme
import lovehan1me.ui.theme.rememberSystemAccentColorOrNull

@Composable
fun ThemeAccentColorPicker(
    selectedId: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = remember { ThemeAccentColor.entries.toList() }
    PickerContainer(
        title = stringResource(Res.string.preset_color_scheme),
        description = stringResource(Res.string.preset_color_scheme_summary),
        modifier = modifier,
    ) {
        items(items = options, key = { it.id }) { option ->
            AccentColorItem(
                option = option,
                selected = selectedId == option.id,
                onClick = { onSelect(option.id) },
            )
        }
    }
}

@Composable
fun VideoLandscapeLayoutStylePicker(
    selectedValue: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        VideoLandscapeLayoutOption(
            value = "classic",
            title = stringResource(Res.string.layout_style_classic),
            previewRes = Res.drawable.bg_settings_pad_classic,
        ),
        VideoLandscapeLayoutOption(
            value = "dual_pane",
            title = stringResource(Res.string.layout_style_dual_pane),
            previewRes = Res.drawable.bg_settings_pad_new,
        ),
    )
    PickerContainer(
        title = stringResource(Res.string.video_landscape_layout_style),
        description = stringResource(Res.string.video_landscape_layout_style_summary),
        modifier = modifier,
    ) {
        items(items = options, key = { it.value }) { option ->
            VideoLandscapeLayoutStyleItem(
                option = option,
                selected = selectedValue == option.value,
                onClick = { onSelect(option.value) },
            )
        }
    }
}

@Composable
fun DarkModePicker(
    selectedValue: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val systemDark = isSystemInDarkTheme()
    val options = listOf(
        DarkModeOption(
            value = "follow_system",
            title = stringResource(Res.string.follow_system),
            iconRes = Res.drawable.ic_lightbulb,
            dark = systemDark,
        ),
        DarkModeOption(
            value = "always_off",
            title = stringResource(Res.string.always_off),
            iconRes = Res.drawable.ic_light_mode,
            dark = false,
        ),
        DarkModeOption(
            value = "always_on",
            title = stringResource(Res.string.always_on),
            iconRes = Res.drawable.ic_dark_mode,
            dark = true,
        ),
    )
    PickerContainer(
        title = stringResource(Res.string.dark_theme),
        description = stringResource(Res.string.dark_mode_picker_summary),
        modifier = modifier,
    ) {
        items(options, key = DarkModeOption::value) { option ->
            DarkModeItem(
                option = option,
                selected = selectedValue == option.value,
                onClick = { onSelect(option.value) },
            )
        }
    }
}

@Composable
fun AppPalettePicker(
    selectedId: Int,
    accentColorId: Int,
    dynamicColor: Boolean,
    darkMode: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = remember { AppPaletteStyle.entries.toList() }
    val isDark = when (darkMode) {
        "always_off" -> false
        "always_on" -> true
        else -> isSystemInDarkTheme()
    }
    val accentColor = ThemeAccentColor.fromId(accentColorId)
    // P6d-4：原 android.R.color.system_accent1_500 直取改为既有 expect（androidMain 实现逐字节等价，
    // SDK_INT>=S 判断已内聚）；桌/iOS 返回 null 回退主题首色，与原 Android <S 行为一致
    val keyColor = (if (dynamicColor) rememberSystemAccentColorOrNull() else null)
        ?: accentColor.colors.first()
    PickerContainer(
        title = stringResource(Res.string.palette_style),
        description = stringResource(Res.string.palette_style_summary),
        modifier = modifier,
    ) {
        items(items = options, key = { it.id }) { style ->
            val previewScheme = expressiveColorScheme(
                keyColor = keyColor,
                isDark = isDark,
                style = style,
            )
            PaletteStyleItem(
                style = style,
                colors = listOf(
                    previewScheme.primary,
                    previewScheme.secondary,
                    previewScheme.tertiary,
                    previewScheme.tertiaryContainer,
                    previewScheme.secondaryContainer,
                    previewScheme.primaryContainer,
                ),
                selected = selectedId == style.id,
                onClick = { onSelect(style.id) },
            )
        }
    }
}

@Composable
private fun PickerContainer(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = HanimeDefaults.Colors.card,
        shape = HanimeDefaults.buttonShape,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = HanimeDefaults.Spacing.itemHorizontal),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawFadedEdge(16.dp, leftEdge = true)
                        drawFadedEdge(16.dp, leftEdge = false)
                    },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun AccentColorItem(
    option: ThemeAccentColor,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 3.dp else (-1).dp,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "accent-color-border",
    )
    PickerOption(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    width = borderWidth,
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.large,
                ),
        ) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(58.dp)
                    .clip(CircleShape),
            ) {
                drawArc(option.colors[1], 180f, 180f, true)
                drawArc(option.colors[2], 0f, 90f, true)
                drawArc(option.colors[3], 90f, 90f, true)
            }
        }
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun VideoLandscapeLayoutStyleItem(
    option: VideoLandscapeLayoutOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 3.dp else (-1).dp,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "video-landscape-layout-border",
    )
    PickerOption(
        onClick = onClick,
        width = 200.dp,
    ) {
        Image(
            painter = painterResource(option.previewRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    width = borderWidth,
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.large,
                ),
        )
        Text(
            text = option.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun DarkModeItem(
    option: DarkModeOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 3.dp else (-1).dp,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "dark-mode-border",
    )
    PickerOption(onClick = onClick) {
        val background = if (option.dark) Color(0xFF1B1B1F) else Color(0xFFFFFBFF)
        val foreground = if (option.dark) Color(0xFFE5E1E6) else Color(0xFF1B1B1F)
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(MaterialTheme.shapes.large)
                .background(background)
                .border(
                    width = borderWidth,
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.large,
                ),
        ) {
            Icon(
                painter = painterResource(option.iconRes),
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Text(
            text = option.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PaletteStyleItem(
    style: AppPaletteStyle,
    colors: List<Color>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 3.dp else (-1).dp,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "palette-style-border",
    )
    PickerOption(onClick = onClick) {
        Column(
            modifier = Modifier
                .width(70.dp)
                .clip(MaterialTheme.shapes.large)
                .border(
                    width = borderWidth,
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.large,
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            colors.forEach { color ->
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .background(color),
                )
            }
        }
        Text(
            text = style.label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PickerOption(
    onClick: () -> Unit,
    width: Dp = 106.dp,
    shapes: ButtonShapes = HanimeDefaults.shapes(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = animatedShape(shapes, interactionSource)
    Column(
        modifier = Modifier
            .width(width)
            .clip(shape)
            .immediateClickable(
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

private data class DarkModeOption(
    val value: String,
    val title: String,
    val iconRes: DrawableResource,
    val dark: Boolean,
)

private data class VideoLandscapeLayoutOption(
    val value: String,
    val title: String,
    val previewRes: DrawableResource,
)

private fun ContentDrawScope.drawFadedEdge(
    edgeWidth: androidx.compose.ui.unit.Dp,
    leftEdge: Boolean
) {
    val edgeWidthPx = edgeWidth.toPx()
    drawRect(
        topLeft = Offset(if (leftEdge) 0f else size.width - edgeWidthPx, 0f),
        size = Size(edgeWidthPx, size.height),
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.Black),
            startX = if (leftEdge) 0f else size.width,
            endX = if (leftEdge) edgeWidthPx else size.width - edgeWidthPx,
        ),
        blendMode = BlendMode.DstIn,
    )
}
