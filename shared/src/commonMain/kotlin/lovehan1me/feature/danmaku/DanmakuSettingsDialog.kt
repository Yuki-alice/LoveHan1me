package lovehan1me.feature.danmaku

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import lovehan1me.Res
import lovehan1me.close
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.danmaku_settings_comment_enabled
import lovehan1me.danmaku_dialog_appearance
import lovehan1me.danmaku_dialog_status
import lovehan1me.danmaku_dialog_title
import lovehan1me.danmaku_dialog_type
import lovehan1me.danmaku_display_area
import lovehan1me.danmaku_settings_enabled
import lovehan1me.danmaku_font_size
import lovehan1me.danmaku_font_size_value
import lovehan1me.danmaku_link_action
import lovehan1me.danmaku_more_settings
import lovehan1me.danmaku_opacity
import lovehan1me.danmaku_percent_value
import lovehan1me.danmaku_speed
import lovehan1me.danmaku_type_bottom
import lovehan1me.danmaku_type_scroll
import lovehan1me.danmaku_type_top
import lovehan1me.danmaku_unlink
import lovehan1me.data.SettingsRepository

/**
 * 播放器内弹幕设置弹窗（学 Kazumi 的设置项，BottomSheet 改 Dialog）。
 *
 * Kazumi 用双 Tab（外观 / 播放）+ 二级弹窗（时间校准 / 屏蔽规则）；
 * 这里压成单页四段，因为我们本来就没有那两样：
 *  - **状况**：当前关联/评论数 + 关联/换一集/取消关联入口（状态条的功能搬进来）
 *  - **外观**：字号 / 不透明度 / 显示区域 / 速度四滑杆（与设置页同一批区间）
 *  - **类型**：滚动 / 顶部 / 底部三 chips（Kazumi `hideScroll/hideTop/hideBottom` 对齐）
 *  - 两个总开关：弹幕总开关 + 评论投影开关
 *
 * 时间校准与屏蔽规则没有（前者要 tracker 加偏移，后者要过滤管线），
 * 弹窗里不摆假入口 —— 哪天引擎支持了再加。
 *
 * 写设置直接落盘（`SettingsRepository.update`），`rememberDanmakuSession` 的
 * effect 会把新值推进 session，正在播放的画面立刻跟着变。
 */
@Composable
fun DanmakuSettingsDialog(
    session: DanmakuSession,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    if (showPicker) {
        DanmakuMatchPickerDialog(
            session = session,
            initialKeyword = session.title,
            onDismiss = { showPicker = false },
        )
        return
    }

    val scope = rememberCoroutineScope()
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    val status by session.status.collectAsStateWithLifecycle()

    fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch { SettingsRepository.update(transform) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.danmaku_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── 状况 ──
                SectionLabel(stringResource(Res.string.danmaku_dialog_status))
                Text(
                    text = danmakuStatusText(status),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showPicker = true }) {
                        Text(stringResource(Res.string.danmaku_link_action))
                    }
                    if (status is DanmakuStatus.Linked) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    session.unlink()
                                }
                            },
                        ) {
                            Text(stringResource(Res.string.danmaku_unlink))
                        }
                    }
                }

                // ── 外观 ──
                SectionLabel(stringResource(Res.string.danmaku_dialog_appearance))
                DanmakuSliderRow(
                    title = stringResource(Res.string.danmaku_font_size),
                    valueText = stringResource(
                        Res.string.danmaku_font_size_value,
                        settings.danmakuFontSizeSp.coerceIn(DANMAKU_FONT_SIZE_RANGE),
                    ),
                    value = settings.danmakuFontSizeSp.coerceIn(DANMAKU_FONT_SIZE_RANGE),
                    range = DANMAKU_FONT_SIZE_RANGE,
                    step = 1,
                    onChange = { newValue -> update { it.copy(danmakuFontSizeSp = newValue) } },
                )
                DanmakuSliderRow(
                    title = stringResource(Res.string.danmaku_opacity),
                    valueText = stringResource(
                        Res.string.danmaku_percent_value,
                        settings.danmakuOpacityPercent.coerceIn(DANMAKU_OPACITY_RANGE),
                    ),
                    value = settings.danmakuOpacityPercent.coerceIn(DANMAKU_OPACITY_RANGE),
                    range = DANMAKU_OPACITY_RANGE,
                    step = 5,
                    onChange = { newValue -> update { it.copy(danmakuOpacityPercent = newValue) } },
                )
                DanmakuSliderRow(
                    title = stringResource(Res.string.danmaku_display_area),
                    valueText = stringResource(
                        Res.string.danmaku_percent_value,
                        settings.danmakuDisplayAreaPercent.coerceIn(DANMAKU_DISPLAY_AREA_RANGE),
                    ),
                    value = settings.danmakuDisplayAreaPercent.coerceIn(DANMAKU_DISPLAY_AREA_RANGE),
                    range = DANMAKU_DISPLAY_AREA_RANGE,
                    step = 5,
                    onChange = { newValue -> update { it.copy(danmakuDisplayAreaPercent = newValue) } },
                )
                DanmakuSliderRow(
                    title = stringResource(Res.string.danmaku_speed),
                    valueText = stringResource(
                        Res.string.danmaku_percent_value,
                        settings.danmakuSpeedPercent.coerceIn(DANMAKU_SPEED_RANGE),
                    ),
                    value = settings.danmakuSpeedPercent.coerceIn(DANMAKU_SPEED_RANGE),
                    range = DANMAKU_SPEED_RANGE,
                    step = 10,
                    onChange = { newValue -> update { it.copy(danmakuSpeedPercent = newValue) } },
                )

                // ── 类型 ──
                SectionLabel(stringResource(Res.string.danmaku_dialog_type))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.danmakuShowScroll,
                        onClick = {
                            update { it.copy(danmakuShowScroll = !settings.danmakuShowScroll) }
                        },
                        label = { Text(stringResource(Res.string.danmaku_type_scroll)) },
                    )
                    FilterChip(
                        selected = settings.danmakuShowTop,
                        onClick = {
                            update { it.copy(danmakuShowTop = !settings.danmakuShowTop) }
                        },
                        label = { Text(stringResource(Res.string.danmaku_type_top)) },
                    )
                    FilterChip(
                        selected = settings.danmakuShowBottom,
                        onClick = {
                            update { it.copy(danmakuShowBottom = !settings.danmakuShowBottom) }
                        },
                        label = { Text(stringResource(Res.string.danmaku_type_bottom)) },
                    )
                }

                // ── 总开关 ×2（复用设置页同一文案） ──
                SwitchRow(
                    title = stringResource(Res.string.danmaku_settings_enabled),
                    checked = settings.danmakuEnabled,
                    onCheckedChange = { checked -> update { it.copy(danmakuEnabled = checked) } },
                )
                SwitchRow(
                    title = stringResource(Res.string.danmaku_settings_comment_enabled),
                    checked = settings.danmakuCommentEnabled,
                    onCheckedChange = { checked -> update { it.copy(danmakuCommentEnabled = checked) } },
                )
            }
        },
        // 代理 / 密钥仍在设置页：弹窗里给个入口，不复制第三遍输入框
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onOpenSettings()
                },
            ) {
                Text(stringResource(Res.string.danmaku_more_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.close))
            }
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 一行滑杆：标题 + 当前值在上，滑杆在下（Kazumi `SettingsSliderTile` 同形）。
 *
 * `onChange` 收的是目标值**而非增量**：调用方直接 `copy` 对应字段，
 * 区间由各调用点的 `range` 保证（滑杆本来就拖不出区间）。
 */
@Composable
private fun DanmakuSliderRow(
    title: String,
    valueText: String,
    value: Int,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step) - 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
    }
}
