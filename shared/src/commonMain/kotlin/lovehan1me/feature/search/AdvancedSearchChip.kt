package lovehan1me.feature.search

import lovehan1me.ui.component.rememberHapticFeedback
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
    val containerColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
    }
    val contentColor = if (checked) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .heightIn(min = 60.dp),
        color = containerColor,
        tonalElevation = if (checked) 2.dp else 0.dp,
        shadowElevation = if (checked) 2.dp else 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
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
                    color = contentColor.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value ?: stringResource(Res.string.filter_all),
                    color = contentColor,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
