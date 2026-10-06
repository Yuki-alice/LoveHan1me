package lovehan1me.app.navigation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import lovehan1me.Res
import lovehan1me.download
import lovehan1me.core.platform.IosDownloadWorkController
import lovehan1me.core.platform.iosDownloadWorkController
import lovehan1me.data.SettingsRepository
import lovehan1me.download_count_limit
import lovehan1me.download_path
import lovehan1me.ic_count
import lovehan1me.no_limit
import lovehan1me.ui.component.SettingSliderItem
import lovehan1me.ui.component.SettingsSectionTitle
import lovehan1me.ui.component.SettingsSegmentedGroup
import lovehan1me.ui.component.lazy.LazyColumn
import org.jetbrains.compose.resources.stringResource

/**
 * iOS 下载设置页（此前 `NavPlaceholder"下载目录（SAF）随 P7"`）。
 *
 * 不复用 commonMain 整屏：路径在沙盒内固定（`Documents/LoveHan1me/downloads`，
 * 文件 App 可见），摆路径选择/恢复默认两个键等于摆死按钮 —— 路径只读展示，
 * 只给并发数滑杆。并发数改完即调 `updateDownloadLimit`（P1-3 已热生效）。
 */
@Composable
fun DownloadSettingsRouteScreen(embedded: Boolean = false) {
    val scope = rememberCoroutineScope()
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    val noLimitText = stringResource(Res.string.no_limit)
    val pathSummary = remember {
        IosDownloadWorkController.currentDownloadDir()
    }

    val content: @Composable () -> Unit = {
        Column {
            if (embedded) {
                SettingsSectionTitle(titleRes = Res.string.download)
            }
            Text(
                text = stringResource(Res.string.download_path) + ": $pathSummary",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsSegmentedGroup {
                SettingSliderItem(
                    title = stringResource(Res.string.download_count_limit),
                    summary = toDownloadCountLimitPrettyString(noLimitText, settings.downloadCountLimit),
                    value = settings.downloadCountLimit,
                    valueRange = 0..10,
                    iconRes = Res.drawable.ic_count,
                    onValueChange = { value ->
                        scope.launch {
                            SettingsRepository.setDownloadCountLimit(value)
                            iosDownloadWorkController().updateDownloadLimit(value)
                        }
                    },
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
