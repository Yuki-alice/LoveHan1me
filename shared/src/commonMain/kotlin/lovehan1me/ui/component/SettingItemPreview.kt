package lovehan1me.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import lovehan1me.ui.preview.HanimePreviewTheme
import lovehan1me.ui.preview.PreviewComponent
import lovehan1me.ui.theme.HanimeDefaults
import androidx.compose.ui.tooling.preview.Preview

/**
 * 设置行 5 种形态一览 + 禁用态。
 *
 * 改 `SettingItem.kt` 后先看这里：标题/摘要的层级、开关与文字的对齐、
 * 禁用态的 38% 削弱是否一致，都能在这一屏里看出来。
 */
@PreviewComponent
@Composable
private fun SettingItemsPreview() {
    HanimePreviewTheme(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(HanimeDefaults.Spacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.small),
        ) {
            SettingSwitchItem(
                title = "深色主题",
                summary = "始终使用深色配色",
                checked = true,
                onCheckedChange = {},
            )
            SettingNavigationItem(
                title = "网络与代理",
                summary = "代理、DoH、Cloudflare 挑战",
                valueText = "系统代理",
                onClick = {},
            )
            SettingInfoItem(
                title = "版本号",
                valueText = "1.0.0",
            )
            SettingSliderItem(
                title = "滑动灵敏度",
                value = 3,
                valueRange = 1..5,
                onValueChange = {},
            )
            SettingChoiceItem(
                title = "樱（Sakura）",
                summary = "Tonal Spot",
                selected = true,
                onClick = {},
            )
            SettingChoiceItem(
                title = "竹（Midori）",
                summary = "Vibrant",
                selected = false,
                onClick = {},
            )
            SettingSwitchItem(
                title = "已禁用的开关",
                summary = "禁用态只削弱一次 38%，不叠加",
                checked = false,
                enabled = false,
                onCheckedChange = {},
            )
        }
    }
}

/** 深色档：确认 onSurfaceVariant 与容器层级在暗色下仍然分得开。 */
@PreviewComponent
@Composable
private fun SettingItemsDarkPreview() {
    HanimePreviewTheme(
        modifier = Modifier.fillMaxWidth(),
        isDark = true,
    ) {
        Column(
            modifier = Modifier.padding(HanimeDefaults.Spacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.small),
        ) {
            SettingSwitchItem(
                title = "深色主题",
                summary = "始终使用深色配色",
                checked = true,
                onCheckedChange = {},
            )
            SettingNavigationItem(
                title = "网络与代理",
                summary = "代理、DoH、Cloudflare 挑战",
                valueText = "系统代理",
                onClick = {},
            )
            SettingChoiceItem(
                title = "樱（Sakura）",
                summary = "Tonal Spot",
                selected = true,
                onClick = {},
            )
        }
    }
}
