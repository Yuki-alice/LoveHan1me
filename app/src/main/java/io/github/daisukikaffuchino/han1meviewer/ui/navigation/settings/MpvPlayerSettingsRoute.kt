package io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.R
import io.github.daisukikaffuchino.han1meviewer.ui.screen.settings.MpvChoiceDialog
import io.github.daisukikaffuchino.han1meviewer.ui.screen.settings.MpvPlayerSettingsScreen
import io.github.daisukikaffuchino.han1meviewer.ui.screen.settings.MpvPlayerSettingsUiState
import kotlinx.coroutines.launch

@Composable
fun MpvPlayerSettingsRouteScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    var activeDialog by remember { mutableStateOf<MpvChoiceDialog?>(null) }
    val uiState = remember(settings, context) { buildMpvPlayerSettingsUiState(context) }

    MpvPlayerSettingsScreen(
        state = uiState,
        profileOptions = listOf(
            stringResource(R.string.profile_fast) to "fast",
            stringResource(R.string.profile_gpu_hq) to "gpu-hq",
        ),
        hwdecOptions = listOf(
            stringResource(R.string.decoding_auto) to "Auto",
            stringResource(R.string.decoding_hw) to "HW",
            stringResource(R.string.decoding_hw_plus) to "HW+",
            stringResource(R.string.decoding_vulkan_copy) to "Vulkan",
            stringResource(R.string.decoding_vulkan) to "Vulkan+",
            stringResource(R.string.decoding_sw) to "SW",
        ),
        activeDialog = activeDialog,
        onOpenProfileDialog = { activeDialog = MpvChoiceDialog.Profile },
        onOpenHwdecDialog = { activeDialog = MpvChoiceDialog.Hwdec },
        onOpenCustomParamsDialog = { activeDialog = MpvChoiceDialog.CustomParams },
        onDismissDialog = { activeDialog = null },
        onProfileChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(mpvProfile = it) } }
        },
        onEnableGpuNextRendererChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(enableGpuNextRenderer = it) } }
        },
        onInterpolationChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(mpvInterpolation = it) } }
        },
        onDebandChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(mpvDeband = it) } }
        },
        onFramedropChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(mpvFramedrop = it) } }
        },
        onHwdecChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(mpvHwdec = it) } }
        },
        onCacheSecsChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(mpvCacheSecs = it) } }
        },
        onTlsVerifyChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(mpvTlsVerify = it) } }
        },
        onNetworkTimeoutChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(mpvNetworkTimeout = it) } }
        },
        onCustomParamsChange = {
            coroutineScope.launch { SettingsRepository.update { settings -> settings.copy(customMpvParams = it) } }
        },
    )
}

private fun buildMpvPlayerSettingsUiState(context: Context): MpvPlayerSettingsUiState {
    val profile = SettingsRepository.mpvProfile
    val hwdec = SettingsRepository.mpvHwdec
    return MpvPlayerSettingsUiState(
        profile = profile,
        profileDisplay = when (profile) {
            "fast" -> context.getString(R.string.profile_fast)
            "gpu-hq" -> context.getString(R.string.profile_gpu_hq)
            else -> profile
        },
        enableGpuNextRenderer = SettingsRepository.enableGPUNextRenderer,
        interpolation = SettingsRepository.mpvInterpolation,
        deband = SettingsRepository.mpvDeband,
        framedrop = SettingsRepository.mpvFramedrop,
        hwdec = hwdec,
        hwdecDisplay = "${context.getString(R.string.mpv_hwdec_summary)} ($hwdec)",
        cacheSecs = SettingsRepository.mpvCacheSecs,
        cacheSecsSummary = "${context.getString(R.string.mpv_cache_secs_summary)} (${SettingsRepository.mpvCacheSecs} S)",
        tlsVerify = SettingsRepository.mpvTlsVerify,
        networkTimeout = SettingsRepository.mpvNetworkTimeout,
        networkTimeoutSummary = "${context.getString(R.string.mpv_network_timeout_summary)} (${SettingsRepository.mpvNetworkTimeout} S)",
        customParams = SettingsRepository.customMpvParams,
    )
}
