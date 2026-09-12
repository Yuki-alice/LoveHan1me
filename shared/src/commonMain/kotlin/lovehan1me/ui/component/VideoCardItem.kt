package lovehan1me.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lovehan1me.Res
import lovehan1me.copy_to_clipboard
import lovehan1me.played
import lovehan1me.now_playing
import lovehan1me.delete
import lovehan1me.h_chan_load_failed
import lovehan1me.h_chan_loading
import lovehan1me.ic_access_time
import lovehan1me.ic_play_circle
import lovehan1me.ic_thumb_up_off_alt
import lovehan1me.data.getHanimeShareText
import lovehan1me.core.domain.model.VideoItemType
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.app.navigation.main.SearchRoute
import lovehan1me.app.navigation.main.navigateToArtistSearch
import lovehan1me.feature.preview.fakeVideosItem
import lovehan1me.ui.component.RetryableImage
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.theme.shapeByInteraction
import lovehan1me.core.util.rememberCopyTextToClipboard
import lovehan1me.core.util.DisplayTextLocalizer
import lovehan1me.core.util.SonnerToast
import kotlinx.coroutines.launch

/**
 * 标准视频卡片项组件。
 *
 * 展示视频封面、标题等信息，支持水平和垂直两种布局。
 * 长按会弹出上下文菜单。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun VideoCardItem(
    modifier: Modifier = Modifier,
    videoItem: VideoItemType,
    isHorizontalCard: Boolean = true,
    isHomePage: Boolean = false,
    isWatched: Boolean = false,
    isPlaying: Boolean = false,
    showDeleteAction: Boolean = false,
    containerColor: Color? = null,
    onClickVideosItem: (String) -> Unit,
    onLongClickVideosItem: (String, String) -> Unit,
) {
    // P6d-4F：原 R.dimen（12sp/14dp）常量化——CMP 资源体系不支持 dimen
    val textFontSize = 12.sp
    val iconSize = 14.dp
    val imageAspectRatio = if (isHorizontalCard) 16f / 9f else 3f / 4f
    val haptic = rememberHapticFeedback()
    val copyTextToClipboard = rememberCopyTextToClipboard()
    // P6d-3-C3：回调内 toast 转 suspend getString，经 scope 桥接
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val pressed by interactionSource.collectIsPressedAsState()
    var showContextMenu by remember { mutableStateOf(false) }
    val currentArtist = videoItem.currentArtist?.takeIf { it.isNotBlank() }
    val cardShape = shapeByInteraction(
        shapes = HanimeDefaults.cardShapes(),
        pressed = pressed,
        animationSpec = HanimeDefaults.shapesDefaultAnimationSpec,
    )
    CardContainerSurface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = cardShape,
        color = containerColor ?: if (isHomePage) {
            HanimeDefaults.Colors.homeVideoCard
        } else {
            HanimeDefaults.Colors.card
        },
    ) {
        Box {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        enabled = !isPlaying,
                        interactionSource = interactionSource,
                        indication = indication,
                        onClick = {
                            haptic()
                            onClickVideosItem(videoItem.videoCode)
                        },
                        onLongClick = {
                            haptic()
                            showContextMenu = true
                        },
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(imageAspectRatio),
                ) {
                    RetryableImage(
                        model = videoItem.coverUrl,
                        contentDescription = videoItem.title,
                        modifier = Modifier.fillMaxSize(),
                        placeholder = painterResource(Res.drawable.h_chan_loading),
                        error = painterResource(Res.drawable.h_chan_load_failed),
                        contentScale = ContentScale.FillWidth,
                    )

                    if (isWatched) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 6.dp)
                                .background(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    shape = MaterialTheme.shapes.extraSmall
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(Res.string.played),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    // 底部半透明遮罩（播放量和时长）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0x9F000000)
                                    ),
                                ),
                            )
                            .padding(horizontal = 6.dp),
                    ) {
                        videoItem.views?.let {
                            Icon(
                                painter = painterResource(Res.drawable.ic_play_circle),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(iconSize),
                            )
                            Text(
                                modifier = Modifier.padding(horizontal = 1.dp),
                                text = DisplayTextLocalizer.localizeViews(it),
                                color = Color.White,
                                fontSize = textFontSize,
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        videoItem.duration?.let {
                            Icon(
                                painter = painterResource(Res.drawable.ic_access_time),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(iconSize),
                            )
                            Text(
                                modifier = Modifier.padding(horizontal = 1.dp),
                                text = it,
                                color = Color.White,
                                fontSize = textFontSize,
                            )
                        }
                    }
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                        shape = MaterialTheme.shapes.largeIncreased
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_play_circle),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(Res.string.now_playing),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                Text(
                    text = videoItem.title,
                    maxLines = 2,
                    minLines = 2,
                    style = MaterialTheme.typography.titleSmall,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                if (currentArtist != null) {
                    Text(
                        text = currentArtist,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
                ) {
                    videoItem.reviews?.takeIf { it.isNotEmpty() }?.let { reviewsText ->
                        Icon(
                            painter = painterResource(Res.drawable.ic_thumb_up_off_alt),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(iconSize),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = reviewsText,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (!videoItem.uploadTime.isNullOrEmpty()) {
                        Text(
                            text = DisplayTextLocalizer.localizeRelativeTime(videoItem.uploadTime!!),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("复制视频信息") },
                    onClick = {
                        showContextMenu = false
                        copyTextToClipboard(
                            getHanimeShareText(
                                videoItem.title,
                                videoItem.videoCode
                            )
                        )
                        scope.launch { SonnerToast.success(getString(Res.string.copy_to_clipboard)) }
                    },
                )
                if (currentArtist != null) {
                    DropdownMenuItem(
                        text = { Text("搜索该作者所有作品") },
                        onClick = {
                            showContextMenu = false
                            navigateToArtistSearch(currentArtist)
                        },
                    )
                }
                if (showDeleteAction) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.delete)) },
                        onClick = {
                            showContextMenu = false
                            onLongClickVideosItem(videoItem.videoCode, videoItem.title)
                        },
                    )
                }
            }
        }
    }
}
