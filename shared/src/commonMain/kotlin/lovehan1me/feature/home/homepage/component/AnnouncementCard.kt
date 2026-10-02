package lovehan1me.feature.home.homepage.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import lovehan1me.ui.component.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lovehan1me.Res
import lovehan1me.close
import lovehan1me.ic_close
import lovehan1me.core.domain.model.Announcement
import lovehan1me.core.domain.model.AnnouncementSeverity

/**
 * 首页上的单条公告卡片。
 *
 * 参数从前是 `List<Announcement>`，实现里却只渲染 `first()` —— 类型承诺"多条"、
 * 行为是"一条"，逼得三个调用方全写 `listOf(x)` 来绕开。现在一条就是一条：
 * 多条公告的浏览交给 [AnnouncementListDialog]。
 *
 * 配色按 [AnnouncementSeverity] 分档：阻断级用 error 容器（与"能忽略"的公告区分开），
 * 其余用 secondary 容器。级别解析时未知值会降级成 Normal（见 `AnnouncementSeverity.fromWire`），
 * 所以这里 `when` 不需要 else 以外的兜底。
 */
@Composable
fun AnnouncementCard(
    announcement: Announcement,
    onAnnouncementClick: (Announcement) -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isBlocking = announcement.severity == AnnouncementSeverity.Blocking
    val container: Color
    val onContainer: Color
    if (isBlocking) {
        container = MaterialTheme.colorScheme.errorContainer
        onContainer = MaterialTheme.colorScheme.onErrorContainer
    } else {
        container = MaterialTheme.colorScheme.secondaryContainer
        onContainer = MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .clickable { onAnnouncementClick(announcement) }
    ) {
        Column(
            modifier = Modifier.padding(
                start = 12.dp,
                end = if (onClose == null) 12.dp else 40.dp,
                top = 10.dp,
                bottom = 12.dp,
            )
        ) {
            if (announcement.title.isNotBlank()) {
                Text(
                    text = announcement.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = onContainer,
                )
            }
            Text(
                text = announcement.content,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = onContainer,
            )
        }

        if (onClose != null) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_close),
                    contentDescription = stringResource(Res.string.close),
                    modifier = Modifier.size(18.dp),
                    tint = onContainer,
                )
            }
        }
    }
}
