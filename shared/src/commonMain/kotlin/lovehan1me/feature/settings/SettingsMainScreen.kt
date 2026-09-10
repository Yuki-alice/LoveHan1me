@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package lovehan1me.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import lovehan1me.core.util.isDebugBuild
import lovehan1me.Res
import lovehan1me.settings_video_playback_summary
import lovehan1me.settings_video_playback
import lovehan1me.settings_player_summary
import lovehan1me.settings_network_download_summary
import lovehan1me.settings_network_download
import lovehan1me.settings_interface_interaction_summary
import lovehan1me.settings_interface_interaction
import lovehan1me.settings_data_privacy_summary
import lovehan1me.settings_data_privacy
import lovehan1me.settings_appearance_summary
import lovehan1me.settings_appearance
import lovehan1me.settings_about_summary
import lovehan1me.player_settings
import lovehan1me.developer_options_summary
import lovehan1me.developer_options
import lovehan1me.about
import lovehan1me.ic_captive_portal
import lovehan1me.ic_code
import lovehan1me.ic_data_table
import lovehan1me.ic_dvr
import lovehan1me.ic_info
import lovehan1me.ic_interests
import lovehan1me.ic_palette
import lovehan1me.ic_video_settings
import lovehan1me.ui.component.SettingNavigationItem
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.ui.theme.HanimeDefaults

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
        if (isDebugBuild()) {
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
