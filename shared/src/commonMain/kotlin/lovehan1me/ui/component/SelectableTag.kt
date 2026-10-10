package lovehan1me.ui.component

import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import lovehan1me.Res
import lovehan1me.ic_check
import org.jetbrains.compose.resources.painterResource

/**
 * 可选标签 —— M3 `FilterChip` 薄封装。
 *
 * 替代此前的自绘 Box+clickable：后者无 Role.Checkbox、无 selected 语义，
 * TalkBack 读不出选中态。FilterChip 自带选中语义、✓ 前导图标与 M3 tonal 配色，
 * 视觉与此前 `primaryContainer/onPrimaryContainer` 一致。
 */
@Composable
fun SelectableTag(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()
    FilterChip(
        selected = selected,
        onClick = {
            haptic()
            onClick()
        },
        label = { Text(text = text) },
        modifier = modifier,
        leadingIcon = if (selected) {
            {
                Icon(
                    painter = painterResource(Res.drawable.ic_check),
                    contentDescription = null,
                )
            }
        } else null,
    )
}
