package lovehan1me.feature.home.homepage.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.close
import lovehan1me.announcement_list
import lovehan1me.core.domain.model.Announcement
import lovehan1me.ui.component.lazy.LazyColumn

/**
 * 多条公告的列表弹窗。
 *
 * 它在上游 Han1meViewer 与本次改造之前**全仓零调用点**（`announcement_list` 字符串
 * 也只被它自己引用）—— 因为首页那条路径压根没有第二个公告能进来。
 * 现在公告真的会是多条了，它才重新有了职责：首页卡片只放最靠前的一条，
 * 其余的从这里进。
 *
 * 已读时机：**点开详情再关掉**才算已读（[onAnnouncementRead]），不是"出现在列表里"就算。
 * 列表里滑过去没看的内容不该被静默吞掉。
 *
 * @param announcements 待展示的公告（调用方应已剔除已读条目）。
 * @param onDismiss 关闭列表时调用。
 * @param onAnnouncementRead 某条公告的详情被关闭时调用，调用方据此落盘已读并重算列表。
 */
@Composable
fun AnnouncementListDialog(
    announcements: List<Announcement>,
    onDismiss: () -> Unit,
    onAnnouncementRead: (Announcement) -> Unit = {},
) {
    var selectedAnnouncement by remember { mutableStateOf<Announcement?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(Res.string.announcement_list),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                items(announcements.size) { index ->
                    val item = announcements[index]
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedAnnouncement = item }
                                .padding(12.dp)
                        ) {
                            Text(
                                text = item.title.ifBlank { item.content },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                            if (item.timestamp > 0) {
                                Text(
                                    text = item.getFormattedDate(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Text(
                stringResource(Res.string.close),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(8.dp)
            )
        }
    )

    selectedAnnouncement?.let { announcement ->
        AnnouncementDialog(
            announcementData = announcement,
            onDismiss = {
                selectedAnnouncement = null
                onAnnouncementRead(announcement)
            }
        )
    }
}
