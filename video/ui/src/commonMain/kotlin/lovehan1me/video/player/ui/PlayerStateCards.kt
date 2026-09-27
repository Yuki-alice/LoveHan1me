/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lovehan1me.video.ui.FilledTonalButton
import lovehan1me.video.ui.PlayerTokens
import lovehan1me.video.ui.Res
import lovehan1me.video.ui.ic_refresh
import lovehan1me.video.ui.playback_finished
import lovehan1me.video.ui.player_play_from_beginning
import lovehan1me.video.ui.replay
import lovehan1me.video.ui.retry
import lovehan1me.video.ui.video_loading_failed
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** 从头播放、播放结束、加载失败重试三张卡：互斥出现，全部只读状态。 */
@Composable
fun BoxScope.PlayerStateCards(
    showResumeButton: Boolean,
    isPlaybackEnded: Boolean,
    showRetry: Boolean,
    isLocked: Boolean,
    errorMessage: String?,
    onResumeClick: () -> Unit,
    onReplay: () -> Unit,
    onRetry: () -> Unit,
) {
    AnimatedVisibility(
        visible = showResumeButton && !isLocked,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = PlayerTokens.PlayerSizes.centerButton),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        ElevatedButton(
            onClick = onResumeClick,
            shape = PlayerTokens.Corners.pill,
        ) {
            Text(stringResource(Res.string.player_play_from_beginning))
        }
    }

    AnimatedVisibility(
        visible = isPlaybackEnded && !isLocked,
        modifier = Modifier.align(Alignment.Center),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = PlayerTokens.Colors.pageSurface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.playback_finished),
                    style = MaterialTheme.typography.titleMedium,
                )

                Spacer(modifier = Modifier.height(PlayerTokens.Spacing.large))

                FilledTonalButton(onClick = onReplay) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_refresh),
                        contentDescription = null,
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(stringResource(Res.string.replay))
                }
            }
        }
    }

    AnimatedVisibility(
        visible = showRetry && !isLocked,
        modifier = Modifier.align(Alignment.Center),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = PlayerTokens.Colors.pageSurface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.video_loading_failed),
                    style = MaterialTheme.typography.titleMedium,
                )

                // 引擎/网络给的真实原因（Media3 错误视图规格：高 32dp / 底边距 64dp /
                // padding 12&4dp / 14sp）。没有原因文本就整块不渲染，只留重试卡。
                errorMessage?.takeIf { it.isNotBlank() }?.let { reason ->
                    Spacer(modifier = Modifier.height(PlayerTokens.Spacing.medium))
                    Box(
                        modifier = Modifier
                            .padding(bottom = 64.dp)
                            .height(PlayerTokens.Spacing.huge)
                            .padding(
                                horizontal = PlayerTokens.Spacing.large,
                                vertical = PlayerTokens.Spacing.small,
                            ),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(PlayerTokens.Spacing.large))

                FilledTonalButton(onClick = onRetry) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_refresh),
                        contentDescription = null,
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(stringResource(Res.string.retry))
                }
            }
        }
    }
}
