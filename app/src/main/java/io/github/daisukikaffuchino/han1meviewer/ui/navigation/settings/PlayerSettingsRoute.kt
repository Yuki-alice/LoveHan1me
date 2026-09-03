package io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.R
import io.github.daisukikaffuchino.han1meviewer.ui.player.PlayerDefaults
import io.github.daisukikaffuchino.han1meviewer.ui.player.PlayerKernel
import io.github.daisukikaffuchino.han1meviewer.ui.screen.settings.PlayerSettingsScreen
import io.github.daisukikaffuchino.han1meviewer.ui.screen.settings.PlayerSettingsUiState
import kotlinx.coroutines.launch

@Composable
fun PlayerSettingsRouteScreen(
    onNavigateToMpvSettings: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    val uiState = remember(settings, context) { buildPlayerSettingsUiState(context) }

    PlayerSettingsScreen(
        state = uiState,
        kernelOptions = PlayerKernel.entries.map { it.name to it.name },
        speedOptions = PlayerDefaults.speedLabels.zip(PlayerDefaults.speeds.map { it.toString() }),
        longPressSpeedOptions = listOf(
            stringResource(R.string.d_speed_times, 1f) to "1",
            stringResource(R.string.d_speed_times, 1.5f) to "1.5",
            stringResource(R.string.d_speed_times, 2f) to "2",
            "${
                stringResource(
                    R.string.d_speed_times,
                    2.5f
                )
            } (${stringResource(R.string.default_)})" to "2.5",
            stringResource(R.string.d_speed_times, 2.8f) to "2.8",
            stringResource(R.string.d_speed_times, 3f) to "3",
            stringResource(R.string.d_speed_times, 3.2f) to "3.2",
            stringResource(R.string.d_speed_times, 3.5f) to "3.5",
            stringResource(R.string.d_speed_times, 3.8f) to "3.8",
            stringResource(R.string.d_speed_times, 4f) to "4",
        ),
        onKernelChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(playerKernel = io.github.daisukikaffuchino.han1meviewer.logic.model.PlayerKernel.fromValue(it)) } }
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

private fun buildPlayerSettingsUiState(context: Context): PlayerSettingsUiState {
    val kernel = SettingsRepository.switchPlayerKernel
    val isMpvPlayer = kernel == PlayerKernel.MpvPlayer.name
    val currentSpeed = SettingsRepository.playerSpeed
    val currentLongPressSpeed = SettingsRepository.longPressSpeedTime
    val speedLabels = PlayerDefaults.speedLabels
    val speedDisplay = speedLabels.getOrElse(
        PlayerDefaults.speeds.indexOfFirst { it == currentSpeed }.takeIf { it >= 0 }
            ?: PlayerDefaults.DEFAULT_SPEED_INDEX
    ) { speedLabels[PlayerDefaults.DEFAULT_SPEED_INDEX] }
    val longPressDisplay = context.getString(R.string.d_speed_times, currentLongPressSpeed)
    val googleCastAvailable = GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    return PlayerSettingsUiState(
        kernel = kernel,
        kernelDisplay = kernel,
        mpvSettingsEnabled = isMpvPlayer,
        mpvSettingsSummary = if (isMpvPlayer) {
            context.getString(R.string.mpv_advanced_settings_summary)
        } else {
            context.getString(R.string.mpv_settings_disabled_summary)
        },
        enableGoogleCast = SettingsRepository.enableGoogleCast,
        googleCastAvailable = googleCastAvailable,
        showBottomProgress = SettingsRepository.showBottomProgress,
        playerSpeed = currentSpeed.toString(),
        playerSpeedLabel = speedDisplay,
        longPressSpeedTimes = currentLongPressSpeed.toString(),
        longPressSpeedTimesLabel = longPressDisplay,
        slideSensitivity = SettingsRepository.slideSensitivity,
        slideSensitivitySummary = toPrettySensitivityString(context, SettingsRepository.slideSensitivity),
    )
}
