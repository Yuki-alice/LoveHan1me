@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.daisukikaffuchino.han1meviewer.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.daisukikaffuchino.han1meviewer.BuildConfig
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.settings_video_playback_summary
import io.github.daisukikaffuchino.han1meviewer.settings_video_playback
import io.github.daisukikaffuchino.han1meviewer.settings_player_summary
import io.github.daisukikaffuchino.han1meviewer.settings_network_download_summary
import io.github.daisukikaffuchino.han1meviewer.settings_network_download
import io.github.daisukikaffuchino.han1meviewer.settings_interface_interaction_summary
import io.github.daisukikaffuchino.han1meviewer.settings_interface_interaction
import io.github.daisukikaffuchino.han1meviewer.settings_data_privacy_summary
import io.github.daisukikaffuchino.han1meviewer.settings_data_privacy
import io.github.daisukikaffuchino.han1meviewer.settings_appearance_summary
import io.github.daisukikaffuchino.han1meviewer.settings_appearance
import io.github.daisukikaffuchino.han1meviewer.settings_about_summary
import io.github.daisukikaffuchino.han1meviewer.player_settings
import io.github.daisukikaffuchino.han1meviewer.developer_options_summary
import io.github.daisukikaffuchino.han1meviewer.developer_options
import io.github.daisukikaffuchino.han1meviewer.about
import io.github.daisukikaffuchino.han1meviewer.ic_captive_portal
import io.github.daisukikaffuchino.han1meviewer.ic_code
import io.github.daisukikaffuchino.han1meviewer.ic_data_table
import io.github.daisukikaffuchino.han1meviewer.ic_dvr
import io.github.daisukikaffuchino.han1meviewer.ic_info
import io.github.daisukikaffuchino.han1meviewer.ic_interests
import io.github.daisukikaffuchino.han1meviewer.ic_palette
import io.github.daisukikaffuchino.han1meviewer.ic_video_settings
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
                title = stringResource(Res.string.settings_appearance),
                summary = stringResource(Res.string.settings_appearance_summary),
                iconRes = Res.drawable.ic_palette,
                shapes = HanimeDefaults.cardShapes(),
                onClick = onOpenAppearance,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(Res.string.settings_interface_interaction),
                summary = stringResource(Res.string.settings_interface_interaction_summary),
                iconRes = Res.drawable.ic_interests,
                shapes = HanimeDefaults.cardShapes(),
                onClick = onOpenInterfaceInteraction,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(Res.string.settings_video_playback),
                summary = stringResource(Res.string.settings_video_playback_summary),
                iconRes = Res.drawable.ic_video_settings,
                shapes = HanimeDefaults.cardShapes(),
                onClick = onOpenVideoPlayback,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(Res.string.player_settings),
                summary = stringResource(Res.string.settings_player_summary),
                iconRes = Res.drawable.ic_dvr,
                shapes = HanimeDefaults.cardShapes(),
                onClick = onOpenPlayerSettings,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(Res.string.settings_network_download),
                summary = stringResource(Res.string.settings_network_download_summary),
                iconRes = Res.drawable.ic_captive_portal,
                shapes = HanimeDefaults.cardShapes(),
                onClick = onOpenNetworkDownload,
            )
        }
        item {
            SettingNavigationItem(
                title = stringResource(Res.string.settings_data_privacy),
                summary = stringResource(Res.string.settings_data_privacy_summary),
                iconRes = Res.drawable.ic_data_table,
                shapes = HanimeDefaults.cardShapes(),
                onClick = onOpenDataPrivacy,
            )
        }
        if (BuildConfig.DEBUG) {
            item {
                SettingNavigationItem(
                    title = stringResource(Res.string.developer_options),
                    summary = stringResource(Res.string.developer_options_summary),
                    iconRes = Res.drawable.ic_code,
                    shapes = HanimeDefaults.cardShapes(),
                    onClick = onOpenDeveloperOptions,
                )
            }
        }
        item {
            SettingNavigationItem(
                title = stringResource(Res.string.about),
                summary = stringResource(Res.string.settings_about_summary),
                iconRes = Res.drawable.ic_info,
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
