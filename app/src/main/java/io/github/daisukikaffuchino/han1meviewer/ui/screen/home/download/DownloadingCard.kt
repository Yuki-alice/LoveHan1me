package io.github.daisukikaffuchino.han1meviewer.ui.screen.home.download

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.daisukikaffuchino.han1meviewer.R
import io.github.daisukikaffuchino.han1meviewer.logic.entity.download.HanimeDownloadEntity
import io.github.daisukikaffuchino.han1meviewer.logic.state.DownloadState
import io.github.daisukikaffuchino.han1meviewer.ui.component.CardContainerSurface
import io.github.daisukikaffuchino.han1meviewer.ui.component.FilledTonalIconButton
import io.github.daisukikaffuchino.han1meviewer.ui.component.IconButton
import io.github.daisukikaffuchino.han1meviewer.ui.preview.ComponentPreview
import io.github.daisukikaffuchino.han1meviewer.ui.preview.fakeHomePageVideos
import io.github.daisukikaffuchino.han1meviewer.ui.theme.HanimeDefaults
import io.github.daisukikaffuchino.han1meviewer.ui.theme.shapeByInteraction
import io.github.daisukikaffuchino.utils.VibrationUtil
import io.github.daisukikaffuchino.utils.formatFileSize

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
    val view = LocalView.current
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
                        VibrationUtil.performHapticFeedback(view)
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
                AsyncImage(
                    model = item.coverUri ?: item.coverUrl,
                    contentDescription = item.title,
                    placeholder = painterResource(R.drawable.h_chan_loading),
                    error = painterResource(R.drawable.h_chan_load_failed),
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
                        text = stringResource(
                            R.string.download_progress_size,
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
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.cancel_download),
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
                                    painter = painterResource(R.drawable.ic_pause),
                                    contentDescription = stringResource(R.string.pause_all),
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
                                    painter = painterResource(R.drawable.ic_play_arrow),
                                    contentDescription = stringResource(R.string.continues),
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
                                    painter = painterResource(R.drawable.ic_refresh),
                                    contentDescription = stringResource(R.string.retry),
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

@Preview(showBackground = true, widthDp = 420, heightDp = 200)
@Composable
private fun PreviewDownloadingItemCard() {
    ComponentPreview {
        DownloadingItemCard(
            item = HanimeDownloadEntity(
                coverUrl = fakeHomePageVideos.first().coverUrl,
                coverUri = null,
                title = fakeHomePageVideos.first().title,
                addDate = System.currentTimeMillis(),
                videoCode = fakeHomePageVideos.first().videoCode,
                videoUri = "sample.mp4",
                quality = "720P",
                videoUrl = "https://example.com/sample.mp4",
                length = 100L * 1024 * 1024,
                downloadedLength = 45L * 1024 * 1024,
                state = DownloadState.Downloading,
                id = 1,
            ),
            onPause = {},
            onResume = {},
            onDelete = {},
        )
    }
}
