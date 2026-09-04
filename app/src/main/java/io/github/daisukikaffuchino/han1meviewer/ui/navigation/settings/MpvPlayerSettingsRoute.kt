package io.github.daisukikaffuchino.han1meviewer.ui.navigation.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.mpv_cache_secs_summary
import io.github.daisukikaffuchino.han1meviewer.mpv_hwdec_summary
import io.github.daisukikaffuchino.han1meviewer.mpv_network_timeout_summary
import io.github.daisukikaffuchino.han1meviewer.profile_gpu_hq
import io.github.daisukikaffuchino.han1meviewer.profile_fast
import io.github.daisukikaffuchino.han1meviewer.decoding_vulkan_copy
import io.github.daisukikaffuchino.han1meviewer.decoding_vulkan
import io.github.daisukikaffuchino.han1meviewer.decoding_sw
import io.github.daisukikaffuchino.han1meviewer.decoding_hw_plus
import io.github.daisukikaffuchino.han1meviewer.decoding_hw
import io.github.daisukikaffuchino.han1meviewer.decoding_auto
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
    // P6d-3-C2：builder 在 remember{} 内无法调资源，字符串在外层预解析后传入
    val profileFastTop = stringResource(Res.string.profile_fast)
    val profileGpuHqTop = stringResource(Res.string.profile_gpu_hq)
    val hwdecSummaryTemplate = stringResource(Res.string.mpv_hwdec_summary)
    val cacheSecsTemplate = stringResource(Res.string.mpv_cache_secs_summary)
    val networkTimeoutTemplate = stringResource(Res.string.mpv_network_timeout_summary)
    val uiState = remember(
        settings, context, profileFastTop, profileGpuHqTop,
        hwdecSummaryTemplate, cacheSecsTemplate, networkTimeoutTemplate,
    ) {
        buildMpvPlayerSettingsUiState(
            profileFastTop, profileGpuHqTop,
            hwdecSummaryTemplate, cacheSecsTemplate, networkTimeoutTemplate,
        )
    }

    MpvPlayerSettingsScreen(
        state = uiState,
        profileOptions = listOf(
            stringResource(Res.string.profile_fast) to "fast",
            stringResource(Res.string.profile_gpu_hq) to "gpu-hq",
        ),
        hwdecOptions = listOf(
            stringResource(Res.string.decoding_auto) to "Auto",
            stringResource(Res.string.decoding_hw) to "HW",
            stringResource(Res.string.decoding_hw_plus) to "HW+",
            stringResource(Res.string.decoding_vulkan_copy) to "Vulkan",
            stringResource(Res.string.decoding_vulkan) to "Vulkan+",
            stringResource(Res.string.decoding_sw) to "SW",
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

private fun buildMpvPlayerSettingsUiState(
    profileFast: String,
    profileGpuHq: String,
    hwdecTemplate: String,
    cacheSecsTemplate: String,
    networkTimeoutTemplate: String,
): MpvPlayerSettingsUiState {
    val profile = SettingsRepository.mpvProfile
    val hwdec = SettingsRepository.mpvHwdec
    return MpvPlayerSettingsUiState(
        profile = profile,
        profileDisplay = when (profile) {
            "fast" -> profileFast
            "gpu-hq" -> profileGpuHq
            else -> profile
        },
        enableGpuNextRenderer = SettingsRepository.enableGPUNextRenderer,
        interpolation = SettingsRepository.mpvInterpolation,
        deband = SettingsRepository.mpvDeband,
        framedrop = SettingsRepository.mpvFramedrop,
        hwdec = hwdec,
        hwdecDisplay = "$hwdecTemplate ($hwdec)",
        cacheSecs = SettingsRepository.mpvCacheSecs,
        cacheSecsSummary = "$cacheSecsTemplate (${SettingsRepository.mpvCacheSecs} S)",
        tlsVerify = SettingsRepository.mpvTlsVerify,
        networkTimeout = SettingsRepository.mpvNetworkTimeout,
        networkTimeoutSummary = "$networkTimeoutTemplate (${SettingsRepository.mpvNetworkTimeout} S)",
        customParams = SettingsRepository.customMpvParams,
    )
}
