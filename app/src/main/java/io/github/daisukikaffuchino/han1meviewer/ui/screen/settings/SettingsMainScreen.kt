@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.daisukikaffuchino.han1meviewer.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.daisukikaffuchino.han1meviewer.BuildConfig
import io.github.daisukikaffuchino.han1meviewer.R
import io.github.daisukikaffuchino.han1meviewer.ui.component.SettingNavigationItem
import io.github.daisukikaffuchino.han1meviewer.ui.component.lazy.LazyColumn
import io.github.daisukikaffuchino.han1meviewer.ui.preview.ComponentPreview
import io.github.daisukikaffuchino.han1meviewer.ui.theme.HanimeDefaults

@Composable
fun SettingsMainScreen(
    onOpenVideoPlayback: () -> Unit,
    onOpenPlayerSettings: () -> Unit,
    onOpenNetworkDownload: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenInterfaceInteraction: () -> Unit,
    onOpenDataPrivacy: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        enableItemAnimation = false,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SettingNavigationItem(
                title = stringResource(R.string.settings_appearance),
                summary = stringResource(R.string.settings_appearance_summary),
                iconRes = R.drawable.ic_palette,
                shapes = HanimeDefaults.cardShapes(),
                onClick = onOpenAppearance,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.settings_interface_interaction),
                summary = stringResource(R.string.settings_interface_interaction_summary),
                iconRes = R.drawable.ic_interests,
                shapes = HanimeDefaults.cardShapes(),
                onClick = onOpenInterfaceInteraction,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.settings_video_playback),
                summary = stringResource(R.string.settings_video_playback_summary),
                iconRes = R.drawable.ic_video_settings,
                shapes = HanimeDefaults.cardShapes(),
                onClick = onOpenVideoPlayback,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.player_settings),
                summary = stringResource(R.string.settings_player_summary),
                iconRes = R.drawable.ic_dvr,
                shapes = HanimeDefaults.cardShapes(),
                onClick = onOpenPlayerSettings,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.settings_network_download),
                summary = stringResource(R.string.settings_network_download_summary),
                iconRes = R.drawable.ic_captive_portal,
                shapes = HanimeDefaults.cardShapes(),
                onClick = onOpenNetworkDownload,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.settings_data_privacy),
                summary = stringResource(R.string.settings_data_privacy_summary),
                iconRes = R.drawable.ic_data_table,
                shapes = HanimeDefaults.cardShapes(),
                onClick = onOpenDataPrivacy,
            )
        }
        if (BuildConfig.DEBUG) {
            item {
                SettingNavigationItem(
                    title = stringResource(R.string.developer_options),
                    summary = stringResource(R.string.developer_options_summary),
                    iconRes = R.drawable.ic_code,
                    shapes = HanimeDefaults.cardShapes(),
                    onClick = onOpenDeveloperOptions,
                )
            }
        }
        item {
            SettingNavigationItem(
                title = stringResource(R.string.about),
                summary = stringResource(R.string.settings_about_summary),
                iconRes = R.drawable.ic_info,
                shapes = HanimeDefaults.cardShapes(),
                onClick = onOpenAbout,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsMainScreenPreview() {
    ComponentPreview {
        SettingsMainScreen({}, {}, {}, {}, {}, {}, {}, {})
    }
}
