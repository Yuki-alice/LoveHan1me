package lovehan1me.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import lovehan1me.ui.component.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import lovehan1me.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.Res
import lovehan1me.view_more_replies
import lovehan1me.report_reason_hint
import lovehan1me.reply
import lovehan1me.ic_thumb_up_off_alt
import lovehan1me.ic_thumb_up_alt
import lovehan1me.ic_thumb_down_off_alt
import lovehan1me.ic_thumb_down_alt
import lovehan1me.ic_report
import lovehan1me.ic_reply
import lovehan1me.logic.model.VideoComments
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.theme.shapeByInteraction
import lovehan1me.core.util.DisplayTextLocalizer

/**
 * 视频评论卡片组件。
 *
 * 展示单条评论，支持回复、点赞、点踩、举报等操作，
 * 可选的更多回复入口。
 *
 * @param modifier 修饰符
 * @param comment 评论数据
 * @param onReply 回复回调
 * @param onThumbUp 点赞回调
 * @param onThumbDown 点踩回调
 * @param onReport 举报回调
 * @param onViewMoreReplies 查看更多回复回调，为 null 时不显示入口
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoCommentCard(
    modifier: Modifier = Modifier,
    comment: VideoComments.VideoComment,
    onReply: () -> Unit,
    onThumbUp: () -> Unit,
    onThumbDown: () -> Unit,
    onReport: () -> Unit,
    onViewMoreReplies: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val pressed by interactionSource.collectIsPressedAsState()
    val cardShape = shapeByInteraction(
        shapes = HanimeDefaults.cardShapes(),
        pressed = pressed,
        animationSpec = HanimeDefaults.shapesDefaultAnimationSpec,
    )

    CardContainerSurface(
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = indication,
                    onClick = {},
                    onLongClick = {},
                )
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HanimeAsyncImage(
                    model = comment.avatar,
                    contentDescription = comment.username,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = comment.username,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = DisplayTextLocalizer.localizeRelativeTime(comment.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(onClick = onReport, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_report),
                        contentDescription = stringResource(Res.string.report_reason_hint),
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
                    )
                }
            }
            SelectionContainer {
                Text(
                    modifier = Modifier.padding(vertical = 8.dp),
                    text = comment.content,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onThumbUp,
                    colors = ButtonDefaults.textButtonColors(),
                ) {
                    Icon(
                        painter = painterResource(
                            if (comment.post.likeCommentStatus) {
                                Res.drawable.ic_thumb_up_alt
                            } else {
                                Res.drawable.ic_thumb_up_off_alt
                            }
                        ),
                        contentDescription = null,
                    )
                    Text(comment.realLikesCount?.toString().orEmpty())
                }

                TextButton(
                    onClick = onThumbDown,
                    colors = ButtonDefaults.textButtonColors(),
                ) {
                    Icon(
                        painter = painterResource(
                            if (comment.post.unlikeCommentStatus) {
                                Res.drawable.ic_thumb_down_alt
                            } else {
                                Res.drawable.ic_thumb_down_off_alt
                            }
                        ),
                        contentDescription = null,
                    )
                }

                TextButton(
                    onClick = onReply,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_reply),
                        contentDescription = null,
                    )
                    Text(stringResource(Res.string.reply))
                }
            }

            if (comment.hasMoreReplies && onViewMoreReplies != null) {
                TextButton(
                    onClick = onViewMoreReplies,
                ) {
                    Text(stringResource(Res.string.view_more_replies, comment.replyCount ?: 0))
                }
            }
        }
    }
}
