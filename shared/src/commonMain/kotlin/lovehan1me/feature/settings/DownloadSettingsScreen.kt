package lovehan1me.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.pref_export_downloads_title
import lovehan1me.pref_export_downloads_summary
import lovehan1me.download_path
import lovehan1me.download_count_limit
import lovehan1me.download
import lovehan1me.ic_count
import lovehan1me.ic_export
import lovehan1me.ic_file_path
import lovehan1me.ui.component.SettingNavigationItem
import lovehan1me.ui.component.SettingSliderItem
import lovehan1me.ui.component.SettingsSectionTitle
import lovehan1me.ui.component.SettingsSegmentedGroup
import lovehan1me.ui.component.lazy.LazyColumn

data class DownloadSettingsUiState(
    val downloadPathSummary: String,
    val downloadCountLimit: Int,
    val downloadCountLimitSummary: String,
)

@Composable
fun DownloadSettingsScreen(
    state: DownloadSettingsUiState,
    maxDownloadCountLimit: Int,
    onOpenDownloadPath: () -> Unit,
    onRestoreDefaultPath: () -> Unit,
    onImportDownloadedFiles: () -> Unit,
    onDownloadCountLimitChange: (Int) -> Unit,
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
