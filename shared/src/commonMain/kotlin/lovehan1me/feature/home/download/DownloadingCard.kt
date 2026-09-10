package lovehan1me.feature.home.download

import androidx.compose.animation.core.animateFloatAsState
import lovehan1me.ui.component.HanimeAsyncImage
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
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
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.Res
import lovehan1me.retry
import lovehan1me.pause_all
import lovehan1me.download_progress_size
import lovehan1me.continues
import lovehan1me.cancel_download
import lovehan1me.h_chan_load_failed
import lovehan1me.h_chan_loading
import lovehan1me.ic_close
import lovehan1me.ic_pause
import lovehan1me.ic_play_arrow
import lovehan1me.ic_refresh
import lovehan1me.data.database.entity.download.HanimeDownloadEntity
import lovehan1me.core.domain.state.DownloadState
import lovehan1me.ui.component.CardContainerSurface
import lovehan1me.ui.component.FilledTonalIconButton
import lovehan1me.ui.component.IconButton
import lovehan1me.feature.preview.fakeHomePageVideos
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.theme.shapeByInteraction
import lovehan1me.core.util.formatFileSize

/**
 * 下载中任务卡片。
 *
 * @param item 下载实体
 * @param onPause 暂停回调
 * @param onResume 恢复回调
 * @param onDelete 删除回调
 * @param modifier 修饰符
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadingItemCard(
    item: HanimeDownloadEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val pressed by interactionSource.collectIsPressedAsState()
    val cardShape = shapeByInteraction(
        shapes = HanimeDefaults.cardShapes(),
        pressed = pressed,
        animationSpec = HanimeDefaults.shapesDefaultAnimationSpec,
    )
    CardContainerSurface(
        modifier = modifier
            .fillMaxWidth(),
        shape = cardShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = indication,
                    onClick = {},
                    onLongClick = {
                        haptic()
                        onDelete()
                    },
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HanimeAsyncImage(
                    model = item.coverUri ?: item.coverUrl,
                    contentDescription = item.title,
                    placeholder = painterResource(Res.drawable.h_chan_loading),
                    error = painterResource(Res.drawable.h_chan_load_failed),
                    modifier = Modifier
                        .width(136.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = item.quality,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            if (item.state == DownloadState.Downloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.6.dp
                                )
                            } else {
                                Icon(
                                    painter = painterResource(downloadStateIcon(item.state)),
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Text(
                                text = downloadStateText(item.state, item.progress),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Text(
                        text = stringResource(Res.string.download_progress_size,
                            item.downloadedLength.formatFileSize(),
                            item.length.formatFileSize(),
                        ),
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = stringResource(Res.string.cancel_download),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }

                    when (item.state) {
                        DownloadState.Downloading -> {
                            FilledTonalIconButton(
                                onClick = onPause,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_pause),
                                    contentDescription = stringResource(Res.string.pause_all),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        DownloadState.Paused,
                        DownloadState.Queued,
                        DownloadState.Unknown -> {
                            FilledTonalIconButton(
                                onClick = onResume,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_play_arrow),
                                    contentDescription = stringResource(Res.string.continues),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        DownloadState.Failed -> {
                            FilledTonalIconButton(
                                onClick = onResume,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_refresh),
                                    contentDescription = stringResource(Res.string.retry),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        DownloadState.Finished -> Unit
                    }
                }
            }

            if (item.state == DownloadState.Downloading) {
                val animatedProgress by animateFloatAsState(
                    targetValue = item.progress / 100f,
                    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                    label = "download-progress",
                )
                LinearWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(
                    progress = { item.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
