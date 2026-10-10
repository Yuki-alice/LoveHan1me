package lovehan1me.feature.search

import lovehan1me.ui.component.rememberHapticFeedback
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import lovehan1me.Res
import lovehan1me.filter_all

/**
 * 高级筛选的条件块：维度名（小字）+ 当前值（大字，单行省略）。
 *
 * 值采用「维度名 / 当前值」上下两行而非 `维度名: 值` 单行拼接，原因：
 * 未选时列宽只有半个面板宽，长值（如 `发行日期: 2026 年 3 月`）必然截断，
 * 而维度名恰恰是最不能截断的信息。分两行后维度名恒完整、值可省略。
 *
 * @param value 当前值；`null` 表示未设置，显示「全部」。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AdvancedSearchChip(
    label: String,
    value: String?,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val haptic = rememberHapticFeedback()
    // M3 tonal 配对：选中 secondaryContainer/onSecondaryContainer（与 FilterChip/SettingChoice 一致），
    // 未选中 surfaceContainerHigh/onSurface。旧 primaryContainer.copy(alpha=0.92) 破坏 tonal 且对比度不可预测。
    val containerColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "advancedChipContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "advancedChipContent",
    )
    // 未选中时维度名走 onSurfaceVariant（次级），选中时与内容同色（容器已足够区分，不再叠 alpha）。
    val labelColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "advancedChipLabel",
    )
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .heightIn(min = 60.dp),
        color = containerColor,
        // MD3 以 tonal 为主：选中靠容器色区分，不再 tonal+shadow 双开。
        tonalElevation = if (checked) 2.dp else 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    role = Role.Checkbox,
                    onClick = {
                        haptic()
                        onClick()
                    },
                    onLongClick = onLongClick?.let { action ->
                        {
                            haptic()
                            action()
                        }
                    },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    color = labelColor,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value ?: stringResource(Res.string.filter_all),
                    color = contentColor,
                    // 关键值走强调档（AppTypography 已 Bold），替代手写 FontWeight.Medium。
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
