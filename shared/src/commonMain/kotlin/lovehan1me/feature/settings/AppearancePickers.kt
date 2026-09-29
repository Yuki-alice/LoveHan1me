@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package lovehan1me.feature.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.theme_board
import lovehan1me.theme_board_summary
import lovehan1me.amoled_mode
import lovehan1me.amoled_mode_summary
import lovehan1me.theme_mode_light
import lovehan1me.theme_mode_dark
import lovehan1me.theme_mode_auto
import lovehan1me.dark_theme
import lovehan1me.dark_mode_picker_summary
import lovehan1me.ic_check
import lovehan1me.ui.component.immediateClickable
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.theme.ThemeBoard
import lovehan1me.ui.theme.animatedShape
import lovehan1me.ui.theme.boardColorScheme

@Composable
fun ThemeBoardPicker(
    selectedId: String,
    darkMode: String,
    contrastLevel: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = remember { ThemeBoard.entries.toList() }
    val isDark = when (darkMode) {
        "always_off" -> false
        "always_on" -> true
        else -> isSystemInDarkTheme()
    }
    val contrastSpec = remember(contrastLevel) {
        lovehan1me.core.domain.model.ContrastLevel.fromValue(contrastLevel).spec
    }
    PickerGridContainer(
        title = stringResource(Res.string.theme_board),
        description = stringResource(Res.string.theme_board_summary),
        modifier = modifier,
    ) {
        options.forEach { board ->
            // 所见即所得：三瓣圆用自己槽位的落地色板渲染（与 HanimeTheme 同一入口）。
            val scheme = boardColorScheme(
                board = board,
                isDark = isDark,
                contrastLevel = contrastSpec,
            )
            BoardBadgeItem(
                board = board,
                primary = scheme.primary,
                tertiary = scheme.tertiary,
                container = scheme.primaryContainer,
                selected = selectedId == board.id,
                onClick = { onSelect(board.id) },
            )
        }
    }
}

@Composable
fun DarkModePicker(
    selectedValue: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 当前主题槽位：手机框用它的落地色板渲染（真所见即所得）。 */
    boardId: String,
    contrastLevel: String,
) {
    val systemDark = isSystemInDarkTheme()
    // 顺序与文案：浅色 / 深色 / 自动 —— 两个明确档在前，唯一的「跟随外部」档放末尾。
    val options = listOf(
        DarkModeOption(
            value = "always_off",
            title = stringResource(Res.string.theme_mode_light),
            dark = false,
        ),
        DarkModeOption(
            value = "always_on",
            title = stringResource(Res.string.theme_mode_dark),
            dark = true,
        ),
        DarkModeOption(
            value = "follow_system",
            title = stringResource(Res.string.theme_mode_auto),
            dark = systemDark,
        ),
    )
    PickerContainer(
        title = stringResource(Res.string.dark_theme),
        description = stringResource(Res.string.dark_mode_picker_summary),
        modifier = modifier,
    ) {
        options.forEach { option ->
            DarkModeItem(
                option = option,
                selected = selectedValue == option.value,
                onClick = { onSelect(option.value) },
                boardId = boardId,
                contrastLevel = contrastLevel,
            )
        }
    }
}

@Composable
private fun PickerContainer(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
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
            PickerHeader(title = title, description = description)
            // 三档排一行，窄窗口下整行可滚。
            // 刻意**不做渐隐边提示**：三个档位在常见宽度下完整可见，常驻渐隐会把
            // 「其实没被裁切」的卡片也压暗一截，比没有提示更误导。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .selectableGroup()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickerGridContainer(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
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
            PickerHeader(title = title, description = description)
            // 自适应网格：鼠标点选为主，彻底不需要横滑（桌面/Windows 鼠标无横向滚轮）。
            // 条目数 = ThemeBoard 槽位数，用 FlowRow 自然换行即可，不引入嵌套滚动。
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun PickerHeader(
    title: String,
    description: String,
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
}

@Composable
private fun DarkModeItem(
    option: DarkModeOption,
    selected: Boolean,
    onClick: () -> Unit,
    boardId: String,
    contrastLevel: String,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val board = remember(boardId) { ThemeBoard.fromId(boardId) }
    val contrastSpec = remember(contrastLevel) {
        lovehan1me.core.domain.model.ContrastLevel.fromValue(contrastLevel).spec
    }
    // 手机框预览：自动 = 对角混搭（左上浅 + 右下深），深/浅 = 整机同调。
    // 颜色全部取当前槽位的落地色板。
    // 注意：boardColorScheme 本身是 @Composable，不能塞进 remember{}，
    // 直接在组合里算（ThemeBoardPicker 同例，开销可接受）。
    val lightScheme = boardColorScheme(board = board, isDark = false, contrastLevel = contrastSpec)
    val darkScheme = boardColorScheme(board = board, isDark = true, contrastLevel = contrastSpec)
    Column(
        modifier = Modifier
            .width(96.dp)
            // 先裁形再挂 clickable：水波纹才被限制在圆角内。
            .clip(HanimeDefaults.buttonShape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .size(width = 96.dp, height = 140.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            when (option.value) {
                "follow_system" -> {
                    PhoneMockupBody(scheme = darkScheme, modifier = Modifier.fillMaxSize())
                    PhoneMockupBody(
                        scheme = lightScheme,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(TopLeftDiagonalShape),
                    )
                }

                else -> PhoneMockupBody(
                    scheme = if (option.dark) darkScheme else lightScheme,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // 选中态由 RadioButton 表达（不再给卡片描边）：圆点在左、文字在右，
        // 与 M3 单选组的读法一致；整块面板（含圆点与文字）都可点。
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                RadioButton(
                    selected = selected,
                    interactionSource = interactionSource,
                    onClick = null,
                )
            }
            Text(
                text = option.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 对角切（左上三角）：自动项的浅色半区。 */
private val TopLeftDiagonalShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(0f, size.height)
    close()
}

/**
 * 迷你手机框：三色条 + 三横线 + 底栏，
 * 只表达配色气质，不追求像素级复刻 App 界面。
 */
@Composable
private fun PhoneMockupBody(
    scheme: ColorScheme,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(scheme.surface)
            .padding(6.dp),
    ) {
        // 顶部三色条：primary / tertiary / secondary。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 16.dp, height = 28.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 4.dp,
                            bottomStart = 4.dp,
                        )
                    )
                    .background(scheme.primary),
            )
            Box(
                modifier = Modifier
                    .size(width = 16.dp, height = 28.dp)
                    .background(scheme.tertiary),
            )
            Box(
                modifier = Modifier
                    .size(width = 16.dp, height = 28.dp)
                    .clip(
                        RoundedCornerShape(
                            topEnd = 4.dp,
                            bottomEnd = 4.dp,
                        )
                    )
                    .background(scheme.secondary),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        // 三横线：primaryContainer / secondaryContainer / tertiaryContainer。
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 7.dp)
                .clip(CircleShape)
                .background(scheme.primaryContainer),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 7.dp)
                .clip(CircleShape)
                .background(scheme.secondaryContainer),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(width = 62.dp, height = 7.dp)
                .clip(CircleShape)
                .background(scheme.tertiaryContainer),
        )
        Spacer(modifier = Modifier.weight(1f))
        // 底栏：圆钮 + 胶囊。
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(16.dp)
                    .clip(CircleShape)
                    .background(scheme.primaryContainer),
            )
        }
    }
}

/**
 * 主题槽位徽：72dp 三瓣圆（上半 primary、下左 tertiary、下右 primaryContainer，
 * 用槽位自己的落地色板，所见即所得）+ 标题/副标题。
 *
 * 横滑在桌面端（尤其 Windows 鼠标）不好用，这里只负责长相；
 * 排布由 [PickerGridContainer] 的自适应网格负责，一律点选。
 */
@Composable
private fun BoardBadgeItem(
    board: ThemeBoard,
    primary: Color,
    tertiary: Color,
    container: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val checkSize by animateDpAsState(
        targetValue = if (selected) 28.dp else 0.dp,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "theme-board-check",
    )
    PickerOption(onClick = onClick) {
        Box(contentAlignment = Alignment.Center) {
            // 三瓣圆：无 Canvas，用裁剪 + 色块叠出来。
            Column(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(primary),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(tertiary),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(container),
                    )
                }
            }
            if (checkSize > 0.dp) {
                Box(
                    modifier = Modifier
                        .size(checkSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Text(
            text = board.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = board.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val dark: Boolean,
)
