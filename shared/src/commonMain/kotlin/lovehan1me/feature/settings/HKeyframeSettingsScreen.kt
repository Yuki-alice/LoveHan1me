package lovehan1me.feature.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.when_countdown_remind
import lovehan1me.show_prompt_when_countdown
import lovehan1me.shared_h_keyframes_use_first_tip
import lovehan1me.shared_h_keyframes_use_first
import lovehan1me.shared_h_keyframes_enable_tip
import lovehan1me.shared_h_keyframes_enable
import lovehan1me.shared_h_keyframe_manage_tip
import lovehan1me.shared_h_keyframe_manage
import lovehan1me.h_keyframes_enable
import lovehan1me.h_keyframe_manage
import lovehan1me.shared
import lovehan1me.manage
import lovehan1me.h_keyframe_settings
import lovehan1me.custom
import lovehan1me.ic_alert
import lovehan1me.ic_count_down
import lovehan1me.ic_format_list_bulleted
import lovehan1me.ic_h_text
import lovehan1me.ic_online_manage
import lovehan1me.ic_share
import lovehan1me.ic_share_first
import lovehan1me.ui.component.SettingNavigationItem
import lovehan1me.ui.component.SettingSliderItem
import lovehan1me.ui.component.SettingSwitchItem
import lovehan1me.ui.component.SettingsAnimatedVisibility
import lovehan1me.ui.component.SettingsSectionTitle
import lovehan1me.ui.component.SettingsSegmentedGroup
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.ui.theme.HanimeDefaults

data class HKeyframeSettingsUiState(
    val hKeyframesEnable: Boolean,
    val hKeyframesSummary: String,
    val sharedHKeyframesEnable: Boolean,
    val sharedHKeyframesUseFirst: Boolean,
    val showCommentWhenCountdown: Boolean,
    val whenCountdownRemind: Int,
    val whenCountdownRemindSummary: String,
)

@Composable
fun HKeyframeSettingsScreen(
    state: HKeyframeSettingsUiState,
    onHKeyframesEnableChange: (Boolean) -> Unit,
    onOpenHKeyframeManage: () -> Unit,
    onSharedHKeyframesEnableChange: (Boolean) -> Unit,
    onSharedHKeyframesUseFirstChange: (Boolean) -> Unit,
    onOpenSharedHKeyframeManage: () -> Unit,
    onShowCommentWhenCountdownChange: (Boolean) -> Unit,
    onWhenCountdownRemindChange: (Int) -> Unit,
    embedded: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        HKeyframeSettingsContent(
            state = state,
            showTitle = embedded,
            onHKeyframesEnableChange = onHKeyframesEnableChange,
            onOpenHKeyframeManage = onOpenHKeyframeManage,
            onSharedHKeyframesEnableChange = onSharedHKeyframesEnableChange,
            onSharedHKeyframesUseFirstChange = onSharedHKeyframesUseFirstChange,
            onOpenSharedHKeyframeManage = onOpenSharedHKeyframeManage,
            onShowCommentWhenCountdownChange = onShowCommentWhenCountdownChange,
            onWhenCountdownRemindChange = onWhenCountdownRemindChange,
        )
    }
    if (embedded) {
        content()
    } else {
        LazyColumn(
            enableItemAnimation = false,
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item { content() }
        }
    }
}

@Composable
private fun HKeyframeSettingsContent(
    state: HKeyframeSettingsUiState,
    showTitle: Boolean,
    onHKeyframesEnableChange: (Boolean) -> Unit,
    onOpenHKeyframeManage: () -> Unit,
    onSharedHKeyframesEnableChange: (Boolean) -> Unit,
    onSharedHKeyframesUseFirstChange: (Boolean) -> Unit,
    onOpenSharedHKeyframeManage: () -> Unit,
    onShowCommentWhenCountdownChange: (Boolean) -> Unit,
    onWhenCountdownRemindChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showTitle) {
            SettingsSectionTitle(titleRes = Res.string.h_keyframe_settings)
        }
        SettingsSegmentedGroup {
            SettingSwitchItem(
                title = stringResource(Res.string.h_keyframes_enable),
                summary = state.hKeyframesSummary,
                checked = state.hKeyframesEnable,
                iconRes = Res.drawable.ic_h_text,
                onCheckedChange = onHKeyframesEnableChange,
            )
        }
        Spacer(Modifier.size(HanimeDefaults.Spacing.small))

        HKeyframeAnimatedSection(
            visible = state.hKeyframesEnable,
            titleRes = Res.string.manage,
        ) {
            SettingNavigationItem(
                title = stringResource(Res.string.h_keyframe_manage),
                iconRes = Res.drawable.ic_format_list_bulleted,
                onClick = onOpenHKeyframeManage,
            )
        }

        HKeyframeAnimatedSection(
            visible = state.hKeyframesEnable,
            titleRes = Res.string.shared,
        ) {
            SettingSwitchItem(
                title = stringResource(Res.string.shared_h_keyframes_enable),
                summary = stringResource(Res.string.shared_h_keyframes_enable_tip),
                checked = state.sharedHKeyframesEnable,
                iconRes = Res.drawable.ic_share,
                onCheckedChange = onSharedHKeyframesEnableChange,
            )
            SettingsAnimatedVisibility(visible = state.sharedHKeyframesEnable) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(
                        HanimeDefaults.Spacing.extraSmall,
                    ),
                ) {
                    SettingSwitchItem(
                        title = stringResource(Res.string.shared_h_keyframes_use_first),
                        summary = stringResource(Res.string.shared_h_keyframes_use_first_tip),
                        checked = state.sharedHKeyframesUseFirst,
                        iconRes = Res.drawable.ic_share_first,
                        onCheckedChange = onSharedHKeyframesUseFirstChange,
                    )
                    SettingNavigationItem(
                        title = stringResource(Res.string.shared_h_keyframe_manage),
                        summary = stringResource(Res.string.shared_h_keyframe_manage_tip),
                        iconRes = Res.drawable.ic_online_manage,
                        onClick = onOpenSharedHKeyframeManage,
                    )
                }
            }
        }

        HKeyframeAnimatedSection(
            visible = state.hKeyframesEnable,
            titleRes = Res.string.custom,
        ) {
            SettingSwitchItem(
                title = stringResource(Res.string.show_prompt_when_countdown),
                checked = state.showCommentWhenCountdown,
                iconRes = Res.drawable.ic_count_down,
                onCheckedChange = onShowCommentWhenCountdownChange,
            )
            SettingSliderItem(
                title = stringResource(Res.string.when_countdown_remind),
                summary = state.whenCountdownRemindSummary,
                value = state.whenCountdownRemind,
                valueRange = 5..30,
                iconRes = Res.drawable.ic_alert,
                onValueChange = onWhenCountdownRemindChange,
            )
        }
    }
}

@Composable
private fun HKeyframeAnimatedSection(
    visible: Boolean,
    titleRes: StringResource,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsAnimatedVisibility(visible = visible) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
        ) {
            SettingsSectionTitle(titleRes = titleRes)
            SettingsSegmentedGroup(content = content)
            Spacer(Modifier.size(HanimeDefaults.Spacing.small))
        }
    }
}
