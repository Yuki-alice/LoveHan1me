package lovehan1me.ui.screen.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import lovehan1me.Res
import lovehan1me.app_source_confirm
import lovehan1me.app_source_douyin_tiktok
import lovehan1me.app_source_forum
import lovehan1me.app_source_github
import lovehan1me.app_source_qq_group
import lovehan1me.app_source_telegram
import lovehan1me.app_source_title
import lovehan1me.app_source_wechat
import lovehan1me.ui.component.HapticTextButton as TextButton

/**
 * M2：来源确认对话框（自 `:app` `MainActivityContent.AppSourceDialog` 下沉，一字未改）。
 * 使用须知（[UsageNoticeDialog]）之后、来源未验证且未挂起时展示。
 */
@Composable
fun AppSourceDialog(
    visible: Boolean,
    onSelect: (String) -> Unit,
) {
    if (!visible) return

    var selectedSource by rememberSaveable { mutableStateOf<String?>(null) }
    val options = listOf(
        stringResource(Res.string.app_source_forum) to "forum",
        stringResource(Res.string.app_source_telegram) to "telegram",
        stringResource(Res.string.app_source_github) to "github",
        stringResource(Res.string.app_source_qq_group) to "qq_group",
        stringResource(Res.string.app_source_wechat) to "wechat",
        stringResource(Res.string.app_source_douyin_tiktok) to "douyin_tiktok",
    )
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(Res.string.app_source_title)) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSource = value }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedSource == value,
                            onClick = { selectedSource = value },
                        )
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedSource != null,
                onClick = { selectedSource?.let(onSelect) },
            ) { Text(stringResource(Res.string.app_source_confirm)) }
        },
    )
}
