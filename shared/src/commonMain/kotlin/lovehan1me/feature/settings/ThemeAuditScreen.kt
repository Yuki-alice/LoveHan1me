package lovehan1me.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.data.SettingsRepository
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.theme.ThemeBoard

/**
 * 配色体检页（debug 入口，挂开发者选项）。
 *
 * 把配色隐性错误变成显性，一屏判定：
 * 1. 当前槽位信息（槽位/明暗/对比度/纯黑）；
 * 2. 层级阶梯（surface → surfaceContainer* 五档，肉眼判是否等距可辨）；
 * 3. 关键角色色卡（含 hex）；
 * 4. 对比度抽查（onXxx on xxx，< 4.5 标红，WCAG AA 正文线）；
 * 5. 真实组件预览（卡片/按钮/文字层级）。
 *
 * 只读 `MaterialTheme.colorScheme`，永远反映**当前生效主题**。
 */
@Composable
fun ThemeAuditScreen(modifier: Modifier = Modifier) {
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    val board = ThemeBoard.fromId(settings.themeId)
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AuditSection(title = "当前槽位") {
            Text(
                text = "${board.title} · ${board.subtitle}  |  " +
                    (if (settings.themeMode.value == "always_on") "深色"
                    else if (settings.themeMode.value == "always_off") "浅色" else "跟随系统") +
                    "  |  对比度 ${settings.contrastLevel.value}" +
                    (if (settings.amoled) "  |  纯黑开" else ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AuditSection(title = "层级阶梯（页底 → 容器五档）") {
            listOf(
                "surface" to scheme.surface,
                "lowest" to scheme.surfaceContainerLowest,
                "low" to scheme.surfaceContainerLow,
                "container" to scheme.surfaceContainer,
                "high" to scheme.surfaceContainerHigh,
                "highest" to scheme.surfaceContainerHighest,
            ).forEach { (name, color) ->
                LadderRow(name = name, color = color)
            }
        }
        AuditSection(title = "关键角色") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "primary" to scheme.primary,
                    "primaryContainer" to scheme.primaryContainer,
                    "secondary" to scheme.secondary,
                    "secondaryContainer" to scheme.secondaryContainer,
                    "tertiary" to scheme.tertiary,
                    "tertiaryContainer" to scheme.tertiaryContainer,
                    "error" to scheme.error,
                    "errorContainer" to scheme.errorContainer,
                    "surfaceDim" to scheme.surfaceDim,
                    "surfaceBright" to scheme.surfaceBright,
                    "outline" to scheme.outline,
                    "outlineVariant" to scheme.outlineVariant,
                    "inverseSurface" to scheme.inverseSurface,
                    "surfaceTint" to scheme.surfaceTint,
                ).forEach { (name, color) ->
                    RoleChip(name = name, color = color)
                }
            }
        }
        AuditSection(title = "对比度抽查（AA 线 4.5）") {
            listOf(
                "onPrimary / primary" to (scheme.onPrimary to scheme.primary),
                "onPrimaryContainer / primaryContainer" to
                    (scheme.onPrimaryContainer to scheme.primaryContainer),
                "onSurface / surface" to (scheme.onSurface to scheme.surface),
                "onSurfaceVariant / highest" to
                    (scheme.onSurfaceVariant to scheme.surfaceContainerHighest),
                "onTertiaryContainer / tertiaryContainer" to
                    (scheme.onTertiaryContainer to scheme.tertiaryContainer),
            ).forEach { (name, pair) ->
                val ratio = contrastRatio(pair.first, pair.second)
                val pass = ratio >= 4.5f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${"%.1f".format(ratio)} ${if (pass) "✓" else "✗"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (pass) scheme.onSurfaceVariant else scheme.error,
                    )
                }
            }
        }
        AuditSection(title = "组件预览") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = HanimeDefaults.Colors.card),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = "卡片标题（onSurface）", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "卡片正文次级信息（onSurfaceVariant）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = {}) { Text("确定") }
                }
            }
        }
    }
}

@Composable
private fun AuditSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
private fun LadderRow(name: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(28.dp)
                .clip(MaterialTheme.shapes.small)
                .background(color),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = color.hex(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RoleChip(name: String, color: Color) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(color),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = color.hex(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Color.hex(): String = "#%06X".format(0xFFFFFF and toArgb())

private fun contrastRatio(foreground: Color, background: Color): Float {
    val l1 = foreground.luminance()
    val l2 = background.luminance()
    return (maxOf(l1, l2) + 0.05f) / (minOf(l1, l2) + 0.05f)
}
