package lovehan1me.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.switch_player_kernel
import lovehan1me.slide_sensitivity
import lovehan1me.mpv_settings_disabled_summary
import lovehan1me.mpv_advanced_settings
import lovehan1me.moderate
import lovehan1me.long_press_speed_summary
import lovehan1me.long_press_speed_multiplier
import lovehan1me.default_playback_speed
import lovehan1me.current_slide_sensitivity
import lovehan1me.player_settings_controls
import lovehan1me.ic_player_setting
import lovehan1me.ic_speed
import lovehan1me.ic_speed_flash
import lovehan1me.ic_touch_long
import lovehan1me.ui.component.ChoiceDialog
import lovehan1me.ui.component.SettingNavigationItem
import lovehan1me.ui.component.SettingsPlainBox
import lovehan1me.ui.component.SettingSliderItem
import lovehan1me.ui.component.segmentedGroup
import lovehan1me.ui.component.segmentedSection
import lovehan1me.ui.component.lazy.LazyColumn

data class PlayerSettingsUiState(
    val kernel: String,
    val kernelDisplay: String,
    /**
     * 本平台是否存在多个**语义不同**的内核（见 `SettingsPlatformCapabilities.playerKernelSelection`）。
     *
     * 桌面与 iOS 的 `createPlaybackEngine` 都忽略 kernel 参数，给出的选项纯属摆设，故不展示。
     */
    val showKernelSelection: Boolean,
    /** 本平台是否可能让「MPV 高级设置」生效（iOS 无 mpv，整项不展示）。 */
    val showMpvSettings: Boolean,
    /** 展示 MPV 入口时它是否可点（Android 需先切到 MPV 内核；桌面恒可点）。 */
    val mpvSettingsEnabled: Boolean,
    val mpvSettingsSummary: String,
    val playerSpeed: String,
    val playerSpeedLabel: String,
    val longPressSpeedTimes: String,
    val longPressSpeedTimesLabel: String,
    val slideSensitivity: Int,
    val slideSensitivitySummary: String,
)

private enum class PlayerChoiceDialog {
    Kernel,
    Speed,
    LongPressSpeed,
}

@Composable
fun PlayerSettingsScreen(
    state: PlayerSettingsUiState,
    kernelOptions: List<Pair<String, String>>,
    speedOptions: List<Pair<String, String>>,
    longPressSpeedOptions: List<Pair<String, String>>,
    onKernelChange: (String) -> Unit,
    onPlayerSpeedChange: (String) -> Unit,
    onLongPressSpeedChange: (String) -> Unit,
    onSlideSensitivityChange: (Int) -> Unit,
    onOpenMpvSettings: () -> Unit,
) {
    var activeDialog by rememberSaveable { mutableStateOf<PlayerChoiceDialog?>(null) }

    ChoiceDialog(
        visible = activeDialog == PlayerChoiceDialog.Kernel,
        title = stringResource(Res.string.switch_player_kernel),
        options = kernelOptions,
        selectedValue = state.kernel,
        onDismiss = { activeDialog = null },
        onSelect = {
            activeDialog = null
            onKernelChange(it)
        },
    )

    ChoiceDialog(
        visible = activeDialog == PlayerChoiceDialog.Speed,
        title = stringResource(Res.string.default_playback_speed),
        options = speedOptions,
        selectedValue = state.playerSpeed,
        onDismiss = { activeDialog = null },
        onSelect = {
            activeDialog = null
            onPlayerSpeedChange(it)
        },
    )

    ChoiceDialog(
        visible = activeDialog == PlayerChoiceDialog.LongPressSpeed,
        title = stringResource(Res.string.long_press_speed_multiplier),
        options = longPressSpeedOptions,
        selectedValue = state.longPressSpeedTimes,
        onDismiss = { activeDialog = null },
        onSelect = {
            activeDialog = null
            onLongPressSpeedChange(it)
        },
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        enableItemAnimation = false,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segmentedSection(titleRes = Res.string.player_settings_controls) {
            segmentedGroup {
                // 内核选择：只有 Android 真有三个语义不同的引擎。桌面/iOS 的
                // createPlaybackEngine 忽略 kernel 参数，列出选项等于骗人。
                if (state.showKernelSelection) {
                    SettingNavigationItem(
                        title = stringResource(Res.string.switch_player_kernel),
                        valueText = state.kernelDisplay,
                        iconRes = Res.drawable.ic_player_setting,
                        onClick = { activeDialog = PlayerChoiceDialog.Kernel },
                    )
                }
                // MPV 高级设置：iOS 无 mpv（AVPlayer），整项不展示；
                // Android 需先切到 MPV 内核才可点；桌面引擎就是 mpv，恒可点。
                if (state.showMpvSettings) {
                    SettingNavigationItem(
                        title = stringResource(Res.string.mpv_advanced_settings),
                        summary = state.mpvSettingsSummary,
                        iconRes = Res.drawable.ic_player_setting,
                        onClick = onOpenMpvSettings,
                        enabled = state.mpvSettingsEnabled,
                        valueText = null,
                    )
                }
                SettingNavigationItem(
                    title = stringResource(Res.string.default_playback_speed),
                    valueText = state.playerSpeedLabel,
                    iconRes = Res.drawable.ic_speed,
                    onClick = { activeDialog = PlayerChoiceDialog.Speed },
                )
                SettingNavigationItem(
                    title = stringResource(Res.string.long_press_speed_multiplier),
                    summary = stringResource(Res.string.long_press_speed_summary,
                        state.longPressSpeedTimesLabel,
                    ),
                    valueText = state.longPressSpeedTimesLabel,
                    iconRes = Res.drawable.ic_touch_long,
                    onClick = { activeDialog = PlayerChoiceDialog.LongPressSpeed },
                )
                SettingSliderItem(
                    title = stringResource(Res.string.slide_sensitivity),
                    summary = state.slideSensitivitySummary,
                    value = state.slideSensitivity,
                    valueRange = 1..7,
                    iconRes = Res.drawable.ic_speed_flash,
                    onValueChange = onSlideSensitivityChange,
                )
            }
        }
    }
}
