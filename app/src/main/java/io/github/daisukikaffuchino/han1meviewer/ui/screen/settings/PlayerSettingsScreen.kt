package io.github.daisukikaffuchino.han1meviewer.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.daisukikaffuchino.han1meviewer.R
import io.github.daisukikaffuchino.han1meviewer.ui.component.ChoiceDialog
import io.github.daisukikaffuchino.han1meviewer.ui.component.SettingNavigationItem
import io.github.daisukikaffuchino.han1meviewer.ui.component.SettingsPlainBox
import io.github.daisukikaffuchino.han1meviewer.ui.component.SettingSliderItem
import io.github.daisukikaffuchino.han1meviewer.ui.component.SettingSwitchItem
import io.github.daisukikaffuchino.han1meviewer.ui.component.segmentedGroup
import io.github.daisukikaffuchino.han1meviewer.ui.component.segmentedSection
import io.github.daisukikaffuchino.han1meviewer.ui.component.lazy.LazyColumn
import io.github.daisukikaffuchino.han1meviewer.ui.preview.ComponentPreview

data class PlayerSettingsUiState(
    val kernel: String,
    val kernelDisplay: String,
    val mpvSettingsEnabled: Boolean,
    val mpvSettingsSummary: String,
    val enableGoogleCast: Boolean,
    val googleCastAvailable: Boolean,
    val showBottomProgress: Boolean,
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
    onEnableGoogleCastChange: (Boolean) -> Unit,
    onShowBottomProgressChange: (Boolean) -> Unit,
    onPlayerSpeedChange: (String) -> Unit,
    onLongPressSpeedChange: (String) -> Unit,
    onSlideSensitivityChange: (Int) -> Unit,
    onOpenMpvSettings: () -> Unit,
) {
    var activeDialog by rememberSaveable { mutableStateOf<PlayerChoiceDialog?>(null) }

    ChoiceDialog(
        visible = activeDialog == PlayerChoiceDialog.Kernel,
        title = stringResource(R.string.switch_player_kernel),
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
        title = stringResource(R.string.default_playback_speed),
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
        title = stringResource(R.string.long_press_speed_multiplier),
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
        segmentedSection(titleRes = R.string.player_settings_controls) {
            segmentedGroup {
                SettingNavigationItem(
                    title = stringResource(R.string.switch_player_kernel),
                    valueText = state.kernelDisplay,
                    iconRes = R.drawable.ic_player_setting,
                    onClick = { activeDialog = PlayerChoiceDialog.Kernel },
                )
                SettingNavigationItem(
                    title = stringResource(R.string.mpv_advanced_settings),
                    summary = state.mpvSettingsSummary,
                    iconRes = R.drawable.ic_player_setting,
                    onClick = onOpenMpvSettings,
                    enabled = state.mpvSettingsEnabled,
                    valueText = null,
                )
                SettingSwitchItem(
                    title = stringResource(R.string.show_bottom_progress),
                    checked = state.showBottomProgress,
                    iconRes = R.drawable.ic_seek_bar,
                    onCheckedChange = onShowBottomProgressChange,
                )
                SettingNavigationItem(
                    title = stringResource(R.string.default_playback_speed),
                    valueText = state.playerSpeedLabel,
                    iconRes = R.drawable.ic_speed,
                    onClick = { activeDialog = PlayerChoiceDialog.Speed },
                )
                SettingNavigationItem(
                    title = stringResource(R.string.long_press_speed_multiplier),
                    summary = stringResource(
                        R.string.long_press_speed_summary,
                        state.longPressSpeedTimesLabel,
                    ),
                    valueText = state.longPressSpeedTimesLabel,
                    iconRes = R.drawable.ic_touch_long,
                    onClick = { activeDialog = PlayerChoiceDialog.LongPressSpeed },
                )
                SettingSliderItem(
                    title = stringResource(R.string.slide_sensitivity),
                    summary = state.slideSensitivitySummary,
                    value = state.slideSensitivity,
                    valueRange = 1..7,
                    iconRes = R.drawable.ic_speed_flash,
                    onValueChange = onSlideSensitivityChange,
                )
            }
        }

        segmentedSection(titleRes = R.string.player_settings_casting) {
            segmentedGroup {
                SettingSwitchItem(
                    title = stringResource(R.string.enable_google_cast),
                    summary = stringResource(
                        if (state.googleCastAvailable) {
                            R.string.enable_google_cast_summary
                        } else {
                            R.string.google_cast_unavailable_summary
                        }
                    ),
                    checked = state.enableGoogleCast,
                    iconRes = R.drawable.ic_cast,
                    onCheckedChange = onEnableGoogleCastChange,
                    enabled = state.googleCastAvailable,
                )
            }
            item {
                SettingsPlainBox(stringResource(R.string.google_cast_warning))
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 760)
@Composable
private fun PlayerSettingsScreenPreview() {
    ComponentPreview {
        PlayerSettingsScreen(
            state = PlayerSettingsUiState(
                kernel = "ExoPlayer",
                kernelDisplay = "ExoPlayer",
                mpvSettingsEnabled = false,
                mpvSettingsSummary = stringResource(R.string.mpv_settings_disabled_summary),
                enableGoogleCast = false,
                googleCastAvailable = true,
                showBottomProgress = true,
                playerSpeed = "1.0",
                playerSpeedLabel = "1.0x",
                longPressSpeedTimes = "2.5",
                longPressSpeedTimesLabel = "2.5倍",
                slideSensitivity = 5,
                slideSensitivitySummary = stringResource(
                    R.string.current_slide_sensitivity,
                    stringResource(R.string.moderate)
                ),
            ),
            kernelOptions = listOf(
                "MediaPlayer" to "MediaPlayer",
                "ExoPlayer" to "ExoPlayer",
                "MpvPlayer" to "MpvPlayer"
            ),
            speedOptions = listOf("1.0x" to "1.0", "1.25x" to "1.25", "1.5x" to "1.5"),
            longPressSpeedOptions = listOf("2.0倍" to "2", "2.5倍" to "2.5", "3.0倍" to "3"),
            onKernelChange = {},
            onEnableGoogleCastChange = {},
            onShowBottomProgressChange = {},
            onPlayerSpeedChange = {},
            onLongPressSpeedChange = {},
            onSlideSensitivityChange = {},
            onOpenMpvSettings = {},
        )
    }
}
