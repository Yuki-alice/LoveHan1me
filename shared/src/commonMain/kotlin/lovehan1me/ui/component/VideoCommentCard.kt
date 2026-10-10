package lovehan1me.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.Res
import lovehan1me.collapse_replies
import lovehan1me.ic_thumb_up_off_alt
import lovehan1me.ic_thumb_up_alt
import lovehan1me.ic_thumb_down_off_alt
import lovehan1me.ic_thumb_down_alt
import lovehan1me.ic_report
import lovehan1me.ic_reply
import lovehan1me.ic_keyboard_arrow_down
import lovehan1me.load_reply_failed
import lovehan1me.loading_replies
import lovehan1me.replies_count
import lovehan1me.reply
import lovehan1me.report_reason_hint
import lovehan1me.retry
import lovehan1me.view_more_replies
import lovehan1me.core.domain.model.VideoComments
import lovehan1me.feature.video.ReplyThread
import lovehan1me.ui.theme.AppEmphasis
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.theme.shapeByInteraction
import lovehan1me.core.util.DisplayTextLocalizer

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoCommentCard(
    modifier: Modifier = Modifier,
    comment: VideoComments.VideoComment,
    onReply: (VideoComments.VideoComment) -> Unit,
    onThumbUp: (VideoComments.VideoComment) -> Unit,
    onThumbDown: (VideoComments.VideoComment) -> Unit,
    onReport: (VideoComments.VideoComment) -> Unit,
    compact: Boolean = false,
    replies: ReplyThread? = null,
    repliesExpanded: Boolean = false,
    onToggleReplies: (() -> Unit)? = null,
    onRetryReplies: (() -> Unit)? = null,
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
        // 子评论是父卡里的一块，不再套一层卡片观感。
        shape = if (compact) MaterialTheme.shapes.medium else cardShape,
        color = if (compact) MaterialTheme.colorScheme.surfaceContainerHighest else null,
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = if (compact) null else indication,
                    onClick = {},
                    onLongClick = {},
                )
                .padding(horizontal = 12.dp, vertical = if (compact) 10.dp else 12.dp),
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
                        .size(if (compact) 32.dp else 40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = comment.username,
                        // 此前是 titleSmall + 手写 Bold；改走强调档。
                        style = AppEmphasis.itemTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = DisplayTextLocalizer.localizeRelativeTime(comment.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(onClick = { onReport(comment) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_report),
                        contentDescription = stringResource(Res.string.report_reason_hint),
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
                    )
                }
            }
            SelectionContainer {
                LinkifiedText(
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
                    onClick = { onThumbUp(comment) },
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
                    onClick = { onThumbDown(comment) },
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
                    onClick = { onReply(comment) },
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_reply),
                        contentDescription = null,
                    )
                    Text(stringResource(Res.string.reply))
                }
            }

            val toggle = onToggleReplies
            if (!compact && comment.hasMoreReplies && toggle != null) {
                TextButton(onClick = toggle) {
                    Text(
                        text = if (repliesExpanded) {
                            stringResource(Res.string.collapse_replies)
                        } else {
                            stringResource(
                                Res.string.view_more_replies,
                                comment.replyCount ?: 0,
                            )
                        },
                    )
                    Icon(
                        painter = painterResource(Res.drawable.ic_keyboard_arrow_down),
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                rotationZ = if (repliesExpanded) 180f else 0f
                            },
                    )
                }

                AnimatedVisibility(
                    visible = repliesExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    ReplyThreadSection(
                        replies = replies,
                        // 重试只重取回覆，不走收起那条路（toggle 在展开态下是"收起"）。
                        onRetry = onRetryReplies ?: toggle,
                        onReply = onReply,
                        onThumbUp = onThumbUp,
                        onThumbDown = onThumbDown,
                        onReport = onReport,
                    )
                }
            }
        }
    }
}

/** 展开后挂在父卡内部的一层回覆：站点按 `loadReplies?id=` 现取，所以有加载中与失败重试两态。 */
@Composable
private fun ReplyThreadSection(
    replies: ReplyThread?,
    onRetry: () -> Unit,
    onReply: (VideoComments.VideoComment) -> Unit,
    onThumbUp: (VideoComments.VideoComment) -> Unit,
    onThumbDown: (VideoComments.VideoComment) -> Unit,
    onReport: (VideoComments.VideoComment) -> Unit,
) {
    when {
        replies == null || (replies.loading && replies.items.isEmpty()) -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(Res.string.loading_replies),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        replies.error != null && replies.items.isEmpty() -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(Res.string.load_reply_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(Res.string.retry))
            }
        }

        else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(Res.string.replies_count, replies.items.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            replies.items.forEach { child ->
                VideoCommentCard(
                    comment = child,
                    compact = true,
                    onReply = onReply,
                    onThumbUp = onThumbUp,
                    onThumbDown = onThumbDown,
                    onReport = onReport,
                )
            }
        }
    }
}
