package me.lovehan1me.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import me.lovehan1me.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import me.lovehan1me.Res
import me.lovehan1me.mpv_tls_verify_summary
import me.lovehan1me.mpv_tls_verify
import me.lovehan1me.mpv_profile
import me.lovehan1me.mpv_network_timeout
import me.lovehan1me.mpv_interpolation_summary
import me.lovehan1me.mpv_interpolation
import me.lovehan1me.mpv_hwdec
import me.lovehan1me.mpv_framedrop_summary
import me.lovehan1me.mpv_framedrop
import me.lovehan1me.mpv_deband_summary
import me.lovehan1me.mpv_deband
import me.lovehan1me.mpv_cache_secs
import me.lovehan1me.enable_gpu_next_summary
import me.lovehan1me.enable_gpu_next
import me.lovehan1me.custom_parameters_title
import me.lovehan1me.custom_parameters_summary
import me.lovehan1me.custom_parameters_example
import me.lovehan1me.custom_parameters
import me.lovehan1me.confirm
import me.lovehan1me.cancel
import me.lovehan1me.player_settings_quality_performance
import me.lovehan1me.player_settings_network_cache
import me.lovehan1me.advanced
import me.lovehan1me.ic_cache
import me.lovehan1me.ic_cert
import me.lovehan1me.ic_chip
import me.lovehan1me.ic_custom
import me.lovehan1me.ic_deband
import me.lovehan1me.ic_decoder
import me.lovehan1me.ic_frame_inter
import me.lovehan1me.ic_frame_jump
import me.lovehan1me.ic_overtime
import me.lovehan1me.ic_render
import me.lovehan1me.ui.component.ChoiceDialog
import me.lovehan1me.ui.component.SettingNavigationItem
import me.lovehan1me.ui.component.SettingSliderItem
import me.lovehan1me.ui.component.SettingSwitchItem
import me.lovehan1me.ui.component.segmentedGroup
import me.lovehan1me.ui.component.segmentedSection
import me.lovehan1me.ui.component.lazy.LazyColumn

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
