package lovehan1me.app.navigation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.data.SettingsRepository
import lovehan1me.Res
import lovehan1me.current_slide_sensitivity
import lovehan1me.default_
import lovehan1me.d_speed_times
import lovehan1me.enable_google_cast_summary
import lovehan1me.extremely_high
import lovehan1me.extremely_low
import lovehan1me.google_cast_unavailable_summary
import lovehan1me.high
import lovehan1me.low
import lovehan1me.moderate
import lovehan1me.mpv_advanced_settings_summary
import lovehan1me.mpv_settings_disabled_summary
import lovehan1me.slightly_high
import lovehan1me.slightly_low
import lovehan1me.feature.player.PlayerDefaults
import lovehan1me.feature.player.isCastAvailable
import lovehan1me.feature.player.PlayerKernel
import lovehan1me.feature.settings.PlayerSettingsScreen
import lovehan1me.feature.settings.PlayerSettingsUiState
import kotlinx.coroutines.launch

@Composable
fun PlayerSettingsRouteScreen(
    onNavigateToMpvSettings: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    // P6d-3-C2：builder 在 remember{} 内无法调 stringResource，字符串在外层预解析后传入
    val kernelForSummary = SettingsRepository.switchPlayerKernel
    val longPressDisplayTop = stringResource(
        Res.string.d_speed_times,
        SettingsRepository.longPressSpeedTime
    )
    val mpvSummaryTop =
        if (kernelForSummary == PlayerKernel.MpvPlayer.name) {
            stringResource(Res.string.mpv_advanced_settings_summary)
        } else {
            stringResource(Res.string.mpv_settings_disabled_summary)
        }
    val sensitivitySummaryTop = toPrettySensitivityString(
        SettingsRepository.slideSensitivity,
        listOf(
            stringResource(Res.string.extremely_low),
            stringResource(Res.string.low),
            stringResource(Res.string.slightly_low),
            stringResource(Res.string.moderate),
            stringResource(Res.string.slightly_high),
            stringResource(Res.string.high),
            stringResource(Res.string.extremely_high),
        ),
        stringResource(Res.string.current_slide_sensitivity),
    )
    val uiState = remember(settings, longPressDisplayTop, mpvSummaryTop, sensitivitySummaryTop) {
        buildPlayerSettingsUiState(longPressDisplayTop, mpvSummaryTop, sensitivitySummaryTop)
    }

    PlayerSettingsScreen(
        state = uiState,
        kernelOptions = PlayerKernel.entries.map { it.name to it.name },
        speedOptions = PlayerDefaults.speedLabels.zip(PlayerDefaults.speeds.map { it.toString() }),
        longPressSpeedOptions = listOf(
            stringResource(Res.string.d_speed_times, 1f) to "1",
            stringResource(Res.string.d_speed_times, 1.5f) to "1.5",
            stringResource(Res.string.d_speed_times, 2f) to "2",
            "${
                stringResource(Res.string.d_speed_times,
                    2.5f
                )
            } (${stringResource(Res.string.default_)})" to "2.5",
            stringResource(Res.string.d_speed_times, 2.8f) to "2.8",
            stringResource(Res.string.d_speed_times, 3f) to "3",
            stringResource(Res.string.d_speed_times, 3.2f) to "3.2",
            stringResource(Res.string.d_speed_times, 3.5f) to "3.5",
            stringResource(Res.string.d_speed_times, 3.8f) to "3.8",
            stringResource(Res.string.d_speed_times, 4f) to "4",
        ),
        onKernelChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(playerKernel = lovehan1me.core.domain.model.PlayerKernel.fromValue(it)) } }
        },
        onEnableGoogleCastChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(enableGoogleCast = it) } }
        },
        onShowBottomProgressChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(showBottomProgress = it) } }
        },
        onPlayerSpeedChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(playerSpeed = it.toFloatOrNull() ?: settings.playerSpeed) } }
        },
        onLongPressSpeedChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(longPressSpeedTime = it.toFloatOrNull() ?: settings.longPressSpeedTime) } }
        },
        onSlideSensitivityChange = {
            coroutineScope.launch { SettingsRepository.setSlideSensitivity(it) }
        },
        onOpenMpvSettings = onNavigateToMpvSettings,
    )
}

private fun buildPlayerSettingsUiState(
    longPressDisplay: String,
    mpvSettingsSummary: String,
    slideSensitivitySummary: String,
): PlayerSettingsUiState {
    val kernel = SettingsRepository.switchPlayerKernel
    val isMpvPlayer = kernel == PlayerKernel.MpvPlayer.name
    val currentSpeed = SettingsRepository.playerSpeed
    val currentLongPressSpeed = SettingsRepository.longPressSpeedTime
    val speedLabels = PlayerDefaults.speedLabels
    val speedDisplay = speedLabels.getOrElse(
        PlayerDefaults.speeds.indexOfFirst { it == currentSpeed }.takeIf { it >= 0 }
            ?: PlayerDefaults.DEFAULT_SPEED_INDEX
    ) { speedLabels[PlayerDefaults.DEFAULT_SPEED_INDEX] }
    val googleCastAvailable = isCastAvailable()
    return PlayerSettingsUiState(
        kernel = kernel,
        kernelDisplay = kernel,
        mpvSettingsEnabled = isMpvPlayer,
        mpvSettingsSummary = mpvSettingsSummary,
        enableGoogleCast = SettingsRepository.enableGoogleCast,
        googleCastAvailable = googleCastAvailable,
        showBottomProgress = SettingsRepository.showBottomProgress,
        playerSpeed = currentSpeed.toString(),
        playerSpeedLabel = speedDisplay,
        longPressSpeedTimes = currentLongPressSpeed.toString(),
        longPressSpeedTimesLabel = longPressDisplay,
        slideSensitivity = SettingsRepository.slideSensitivity,
        slideSensitivitySummary = slideSensitivitySummary,
    )
}
