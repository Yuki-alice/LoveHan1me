package lovehan1me.app.navigation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.data.SettingsRepository
import lovehan1me.Res
import lovehan1me.mpv_cache_secs_summary
import lovehan1me.mpv_hwdec_summary
import lovehan1me.mpv_network_timeout_summary
import lovehan1me.profile_gpu_hq
import lovehan1me.profile_fast
import lovehan1me.decoding_vulkan_copy
import lovehan1me.decoding_vulkan
import lovehan1me.decoding_sw
import lovehan1me.decoding_hw_plus
import lovehan1me.decoding_hw
import lovehan1me.decoding_auto
import lovehan1me.feature.settings.MpvChoiceDialog
import lovehan1me.feature.settings.MpvPlayerSettingsScreen
import lovehan1me.feature.settings.MpvPlayerSettingsUiState
import lovehan1me.core.platform.SettingsPlatformCapabilities
import lovehan1me.core.platform.settingsPlatformCapabilities
import kotlinx.coroutines.launch

@Composable
fun MpvPlayerSettingsRouteScreen() {
    val coroutineScope = rememberCoroutineScope()
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    var activeDialog by remember { mutableStateOf<MpvChoiceDialog?>(null) }
    // 平台能力：hwdec 档位与 vo 开关的可见性由平台决定，不再三端列出同一份选项。
    val capabilities = remember { settingsPlatformCapabilities() }
    // P6d-3-C2：builder 在 remember{} 内无法调资源，字符串在外层预解析后传入
    val profileFastTop = stringResource(Res.string.profile_fast)
    val profileGpuHqTop = stringResource(Res.string.profile_gpu_hq)
    val hwdecSummaryTemplate = stringResource(Res.string.mpv_hwdec_summary)
    val cacheSecsTemplate = stringResource(Res.string.mpv_cache_secs_summary)
    val networkTimeoutTemplate = stringResource(Res.string.mpv_network_timeout_summary)
    val uiState = remember(
        settings, profileFastTop, profileGpuHqTop,
        hwdecSummaryTemplate, cacheSecsTemplate, networkTimeoutTemplate,
    ) {
        buildMpvPlayerSettingsUiState(
            capabilities = capabilities,
            profileFast = profileFastTop,
            profileGpuHq = profileGpuHqTop,
            hwdecTemplate = hwdecSummaryTemplate,
            cacheSecsTemplate = cacheSecsTemplate,
            networkTimeoutTemplate = networkTimeoutTemplate,
        )
    }

    MpvPlayerSettingsScreen(
        state = uiState,
        profileOptions = listOf(
            stringResource(Res.string.profile_fast) to "fast",
            stringResource(Res.string.profile_gpu_hq) to "gpu-hq",
        ),
        hwdecOptions = if (capabilities.mpvMediacodecHwdec) {
            // Android：六档都有真实语义（mediacodec / vulkan-copy 是 Android 专属解码器）。
            listOf(
                stringResource(Res.string.decoding_auto) to "Auto",
                stringResource(Res.string.decoding_hw) to "HW",
                stringResource(Res.string.decoding_hw_plus) to "HW+",
                stringResource(Res.string.decoding_vulkan_copy) to "Vulkan",
                stringResource(Res.string.decoding_vulkan) to "Vulkan+",
                stringResource(Res.string.decoding_sw) to "SW",
            )
        } else {
            // 桌面：没有 mediacodec，"HW/HW+/Vulkan/Vulkan+" 全部折叠到 mpv 的自动硬件解码
            // （macOS=videotoolbox / Windows=d3d11va / Linux=vaapi），列出四档是假选择。
            // 只保留两个真实可区分的语义：自动硬解 / 强制软解。
            listOf(
                stringResource(Res.string.decoding_auto) to "Auto",
                stringResource(Res.string.decoding_sw) to "SW",
            )
        },
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
    capabilities: SettingsPlatformCapabilities,
    profileFast: String,
    profileGpuHq: String,
    hwdecTemplate: String,
    cacheSecsTemplate: String,
    networkTimeoutTemplate: String,
): MpvPlayerSettingsUiState {
    val profile = SettingsRepository.mpvProfile
    val storedHwdec = SettingsRepository.mpvHwdec
    // 桌面侧只提供 Auto / SW 两档；历史上存过 "HW+"/"Vulkan" 的话，对话框里不会有任何
    // 选项处于选中态，摘要也会显示一个本平台不存在的档位。故**只做展示归一化**
    // （不动用户设置）：非 SW 一律按 Auto 呈现 —— 与 DesktopMpvPlaybackEngine
    // applyMpvSettings 的下发映射一致。
    val hwdec = if (capabilities.mpvMediacodecHwdec || storedHwdec == "SW") storedHwdec else "Auto"
    return MpvPlayerSettingsUiState(
        profile = profile,
        profileDisplay = when (profile) {
            "fast" -> profileFast
            "gpu-hq" -> profileGpuHq
            else -> profile
        },
        showVideoOutput = capabilities.mpvVideoOutput,
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
