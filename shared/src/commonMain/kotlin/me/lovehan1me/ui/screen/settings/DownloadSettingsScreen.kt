package me.lovehan1me.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import me.lovehan1me.Res
import me.lovehan1me.pref_export_downloads_title
import me.lovehan1me.pref_export_downloads_summary
import me.lovehan1me.download_speed_limit
import me.lovehan1me.download_path
import me.lovehan1me.download_count_limit
import me.lovehan1me.download
import me.lovehan1me.ic_count
import me.lovehan1me.ic_export
import me.lovehan1me.ic_file_path
import me.lovehan1me.ic_speed
import me.lovehan1me.ui.component.SettingNavigationItem
import me.lovehan1me.ui.component.SettingSliderItem
import me.lovehan1me.ui.component.SettingsSectionTitle
import me.lovehan1me.ui.component.SettingsSegmentedGroup
import me.lovehan1me.ui.component.lazy.LazyColumn

data class DownloadSettingsUiState(
    val downloadPathSummary: String,
    val downloadCountLimit: Int,
    val downloadCountLimitSummary: String,
    val downloadSpeedLimitIndex: Int,
    val downloadSpeedLimitSummary: String,
)

@Composable
fun DownloadSettingsScreen(
    state: DownloadSettingsUiState,
    maxDownloadCountLimit: Int,
    maxDownloadSpeedLimitIndex: Int,
    onOpenDownloadPath: () -> Unit,
    onRestoreDefaultPath: () -> Unit,
    onImportDownloadedFiles: () -> Unit,
    onDownloadCountLimitChange: (Int) -> Unit,
    onDownloadSpeedLimitChange: (Int) -> Unit,
    embedded: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        Column {
            if (embedded) {
                SettingsSectionTitle(titleRes = Res.string.download)
            }
            SettingsSegmentedGroup {
                SettingNavigationItem(
                    title = stringResource(Res.string.download_path),
                    summary = state.downloadPathSummary,
                    iconRes = Res.drawable.ic_file_path,
                    onClick = onOpenDownloadPath,
                )
                SettingNavigationItem(
                    title = stringResource(Res.string.pref_export_downloads_title),
                    summary = stringResource(Res.string.pref_export_downloads_summary),
                    iconRes = Res.drawable.ic_export,
                    onClick = onImportDownloadedFiles,
                )
                SettingSliderItem(
                    title = stringResource(Res.string.download_count_limit),
                    summary = state.downloadCountLimitSummary,
                    value = state.downloadCountLimit,
                    valueRange = 0..maxDownloadCountLimit,
                    iconRes = Res.drawable.ic_count,
                    onValueChange = onDownloadCountLimitChange,
                )
                SettingSliderItem(
                    title = stringResource(Res.string.download_speed_limit),
                    summary = state.downloadSpeedLimitSummary,
                    value = state.downloadSpeedLimitIndex,
                    valueRange = 0..maxDownloadSpeedLimitIndex,
                    iconRes = Res.drawable.ic_speed,
                    onValueChange = onDownloadSpeedLimitChange,
                )
            }
        }
    }
    if (embedded) {
        content()
    } else {
        LazyColumn(
            enableItemAnimation = false,
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item { content() }
        }
    }
}
