package io.github.daisukikaffuchino.han1meviewer.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import io.github.daisukikaffuchino.han1meviewer.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.mpv_tls_verify_summary
import io.github.daisukikaffuchino.han1meviewer.mpv_tls_verify
import io.github.daisukikaffuchino.han1meviewer.mpv_profile
import io.github.daisukikaffuchino.han1meviewer.mpv_network_timeout
import io.github.daisukikaffuchino.han1meviewer.mpv_interpolation_summary
import io.github.daisukikaffuchino.han1meviewer.mpv_interpolation
import io.github.daisukikaffuchino.han1meviewer.mpv_hwdec
import io.github.daisukikaffuchino.han1meviewer.mpv_framedrop_summary
import io.github.daisukikaffuchino.han1meviewer.mpv_framedrop
import io.github.daisukikaffuchino.han1meviewer.mpv_deband_summary
import io.github.daisukikaffuchino.han1meviewer.mpv_deband
import io.github.daisukikaffuchino.han1meviewer.mpv_cache_secs
import io.github.daisukikaffuchino.han1meviewer.enable_gpu_next_summary
import io.github.daisukikaffuchino.han1meviewer.enable_gpu_next
import io.github.daisukikaffuchino.han1meviewer.custom_parameters_title
import io.github.daisukikaffuchino.han1meviewer.custom_parameters_summary
import io.github.daisukikaffuchino.han1meviewer.custom_parameters_example
import io.github.daisukikaffuchino.han1meviewer.custom_parameters
import io.github.daisukikaffuchino.han1meviewer.confirm
import io.github.daisukikaffuchino.han1meviewer.cancel
import io.github.daisukikaffuchino.han1meviewer.player_settings_quality_performance
import io.github.daisukikaffuchino.han1meviewer.player_settings_network_cache
import io.github.daisukikaffuchino.han1meviewer.advanced
import io.github.daisukikaffuchino.han1meviewer.ic_cache
import io.github.daisukikaffuchino.han1meviewer.ic_cert
import io.github.daisukikaffuchino.han1meviewer.ic_chip
import io.github.daisukikaffuchino.han1meviewer.ic_custom
import io.github.daisukikaffuchino.han1meviewer.ic_deband
import io.github.daisukikaffuchino.han1meviewer.ic_decoder
import io.github.daisukikaffuchino.han1meviewer.ic_frame_inter
import io.github.daisukikaffuchino.han1meviewer.ic_frame_jump
import io.github.daisukikaffuchino.han1meviewer.ic_overtime
import io.github.daisukikaffuchino.han1meviewer.ic_render
import io.github.daisukikaffuchino.han1meviewer.ui.component.ChoiceDialog
import io.github.daisukikaffuchino.han1meviewer.ui.component.SettingNavigationItem
import io.github.daisukikaffuchino.han1meviewer.ui.component.SettingSliderItem
import io.github.daisukikaffuchino.han1meviewer.ui.component.SettingSwitchItem
import io.github.daisukikaffuchino.han1meviewer.ui.component.segmentedGroup
import io.github.daisukikaffuchino.han1meviewer.ui.component.segmentedSection
import io.github.daisukikaffuchino.han1meviewer.ui.component.lazy.LazyColumn
import io.github.daisukikaffuchino.han1meviewer.ui.preview.ComponentPreview

data class MpvPlayerSettingsUiState(
    val profile: String,
    val profileDisplay: String,
    val enableGpuNextRenderer: Boolean,
    val interpolation: Boolean,
    val deband: Boolean,
    val framedrop: Boolean,
    val hwdec: String,
    val hwdecDisplay: String,
    val cacheSecs: Int,
    val cacheSecsSummary: String,
    val tlsVerify: Boolean,
    val networkTimeout: Int,
    val networkTimeoutSummary: String,
    val customParams: String,
)

enum class MpvChoiceDialog {
    Profile,
    Hwdec,
    CustomParams,
}

@Composable
fun MpvPlayerSettingsScreen(
    state: MpvPlayerSettingsUiState,
    profileOptions: List<Pair<String, String>>,
    hwdecOptions: List<Pair<String, String>>,
    activeDialog: MpvChoiceDialog?,
    onOpenProfileDialog: () -> Unit,
    onOpenHwdecDialog: () -> Unit,
    onOpenCustomParamsDialog: () -> Unit,
    onDismissDialog: () -> Unit,
    onProfileChange: (String) -> Unit,
    onEnableGpuNextRendererChange: (Boolean) -> Unit,
    onInterpolationChange: (Boolean) -> Unit,
    onDebandChange: (Boolean) -> Unit,
    onFramedropChange: (Boolean) -> Unit,
    onHwdecChange: (String) -> Unit,
    onCacheSecsChange: (Int) -> Unit,
    onTlsVerifyChange: (Boolean) -> Unit,
    onNetworkTimeoutChange: (Int) -> Unit,
    onCustomParamsChange: (String) -> Unit,
) {
    ChoiceDialog(
        visible = activeDialog == MpvChoiceDialog.Profile,
        title = stringResource(Res.string.mpv_profile),
        options = profileOptions,
        selectedValue = state.profile,
        onDismiss = onDismissDialog,
        onSelect = { onDismissDialog(); onProfileChange(it) },
    )

    ChoiceDialog(
        visible = activeDialog == MpvChoiceDialog.Hwdec,
        title = stringResource(Res.string.mpv_hwdec),
        options = hwdecOptions,
        selectedValue = state.hwdec,
        onDismiss = onDismissDialog,
        onSelect = { onDismissDialog(); onHwdecChange(it) },
    )

    if (activeDialog == MpvChoiceDialog.CustomParams) {
        CustomParamsDialog(
            value = state.customParams,
            onDismiss = onDismissDialog,
            onConfirm = { onDismissDialog(); onCustomParamsChange(it) },
        )
    }

    LazyColumn(
        enableItemAnimation = false,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segmentedSection(titleRes = Res.string.player_settings_quality_performance) {
            segmentedGroup {
                SettingNavigationItem(
                    title = stringResource(Res.string.mpv_profile),
                    valueText = state.profileDisplay,
                    iconRes = Res.drawable.ic_render,
                    onClick = onOpenProfileDialog,
                )
                SettingSwitchItem(
                    title = stringResource(Res.string.enable_gpu_next),
                    summary = stringResource(Res.string.enable_gpu_next_summary),
                    checked = state.enableGpuNextRenderer,
                    iconRes = Res.drawable.ic_chip,
                    onCheckedChange = onEnableGpuNextRendererChange,
                )
                SettingSwitchItem(
                    title = stringResource(Res.string.mpv_interpolation),
                    summary = stringResource(Res.string.mpv_interpolation_summary),
                    checked = state.interpolation,
                    iconRes = Res.drawable.ic_frame_inter,
                    onCheckedChange = onInterpolationChange,
                )
                SettingSwitchItem(
                    title = stringResource(Res.string.mpv_deband),
                    summary = stringResource(Res.string.mpv_deband_summary),
                    checked = state.deband,
                    iconRes = Res.drawable.ic_deband,
                    onCheckedChange = onDebandChange,
                )
                SettingSwitchItem(
                    title = stringResource(Res.string.mpv_framedrop),
                    summary = stringResource(Res.string.mpv_framedrop_summary),
                    checked = state.framedrop,
                    iconRes = Res.drawable.ic_frame_jump,
                    onCheckedChange = onFramedropChange,
                )
                SettingNavigationItem(
                    title = stringResource(Res.string.mpv_hwdec),
                    summary = state.hwdecDisplay,
                    iconRes = Res.drawable.ic_decoder,
                    onClick = onOpenHwdecDialog,
                )
            }
        }

        segmentedSection(titleRes = Res.string.player_settings_network_cache) {
            segmentedGroup {
                SettingSliderItem(
                    title = stringResource(Res.string.mpv_cache_secs),
                    summary = state.cacheSecsSummary,
                    value = state.cacheSecs,
                    valueRange = 10..120,
                    step = 5,
                    iconRes = Res.drawable.ic_cache,
                    onValueChange = onCacheSecsChange,
                )
                SettingSwitchItem(
                    title = stringResource(Res.string.mpv_tls_verify),
                    summary = stringResource(Res.string.mpv_tls_verify_summary),
                    checked = state.tlsVerify,
                    iconRes = Res.drawable.ic_cert,
                    onCheckedChange = onTlsVerifyChange,
                )
                SettingSliderItem(
                    title = stringResource(Res.string.mpv_network_timeout),
                    summary = state.networkTimeoutSummary,
                    value = state.networkTimeout,
                    valueRange = 5..30,
                    iconRes = Res.drawable.ic_overtime,
                    onValueChange = onNetworkTimeoutChange,
                )
            }
        }

        segmentedSection(titleRes = Res.string.advanced) {
            segmentedGroup {
                SettingNavigationItem(
                    title = stringResource(Res.string.custom_parameters),
                    summary = state.customParams.ifBlank { stringResource(Res.string.custom_parameters_summary) },
                    iconRes = Res.drawable.ic_custom,
                    onClick = onOpenCustomParamsDialog,
                )
            }
        }
    }
}

@Composable
private fun CustomParamsDialog(
    value: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val text = remember { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.custom_parameters_title)) },
        text = {
            OutlinedTextField(
                value = text.value,
                onValueChange = { text.value = it },
                label = { Text(stringResource(Res.string.custom_parameters_example)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.value) }) {
                Text(stringResource(Res.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun MpvPlayerSettingsScreenPreview() {
    ComponentPreview {
        MpvPlayerSettingsScreen(
            state = MpvPlayerSettingsUiState(
                profile = "fast",
                profileDisplay = "效能優先 (fast)",
                enableGpuNextRenderer = false,
                interpolation = false,
                deband = true,
                framedrop = true,
                hwdec = "Auto",
                hwdecDisplay = "利用硬體加速提升播放效能 (Auto)",
                cacheSecs = 60,
                cacheSecsSummary = "調整快取大小以適應網路波動 (60 S)",
                tlsVerify = true,
                networkTimeout = 10,
                networkTimeoutSummary = "控制請求等待時間 (10 S)",
                customParams = "",
            ),
            profileOptions = listOf(
                "效能優先 (fast)" to "fast",
                "畫質優先 (gpu-hq)" to "gpu-hq",
            ),
            hwdecOptions = listOf(
                "自動選擇 (auto)" to "Auto",
                "硬體解碼 (HW: mediacodec-copy)" to "HW",
            ),
            activeDialog = null,
            onOpenProfileDialog = {},
            onOpenHwdecDialog = {},
            onOpenCustomParamsDialog = {},
            onDismissDialog = {},
            onProfileChange = {},
            onEnableGpuNextRendererChange = {},
            onInterpolationChange = {},
            onDebandChange = {},
            onFramedropChange = {},
            onHwdecChange = {},
            onCacheSecsChange = {},
            onTlsVerifyChange = {},
            onNetworkTimeoutChange = {},
            onCustomParamsChange = {},
        )
    }
}
