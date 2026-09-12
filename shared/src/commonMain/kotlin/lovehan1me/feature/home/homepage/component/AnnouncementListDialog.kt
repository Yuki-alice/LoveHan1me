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
import lovehan1me.feature.preview.fakeAnnouncements

/**
 * 显示完整公告列表弹窗。
 *
 * @param announcements 可供选择的公告列表。
 * @param onDismiss 关闭弹窗时调用。
 */
@Composable
fun AnnouncementListDialog(
    announcements: List<Announcement>,
    onDismiss: () -> Unit
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
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
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
            onDismiss = { selectedAnnouncement = null }
        )
    }
}
