package lovehan1me.feature.danmaku

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import lovehan1me.Res
import lovehan1me.data.danmaku.DanmakuEpisodeRef
import lovehan1me.danmaku_status_comment_only
import lovehan1me.danmaku_status_linked
import lovehan1me.danmaku_status_linked_mixed
import lovehan1me.danmaku_status_loading
import lovehan1me.danmaku_status_no_danmaku
import lovehan1me.danmaku_status_off
import lovehan1me.danmaku_status_unavailable
import lovehan1me.danmaku_status_unmatched
import lovehan1me.ic_settings
import lovehan1me.ui.component.IconButton
import lovehan1me.ui.theme.HanimeDefaults

/**
 * 底栏中间位的弹幕设置钮。
 *
 * 此前这里是"开关 + 设置"双钮。开关与启停栏的 `PlayerControllerDefaults.DanmakuIcon`
 * 是同一件事的两个入口 —— 同一条动作在底栏摆两处，用户得先判断"这两个有什么不一样"
 * 才敢点。开关只留启停栏那一个，这里只剩设置。
 *
 * 设置钮**不自己弹窗**：底栏随控件自动隐藏被销毁，弹窗挂在这里会被连带销毁。
 * 弹窗改由调用方挂在播放器最上层槽（`VideoPlayerShell` 的 `dialogHost`），
 * 本控件只上报"有人点了设置"。
 */
@Composable
fun DanmakuControls(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenSettings) {
            Icon(
                painter = painterResource(Res.drawable.ic_settings),
                contentDescription = null,
                tint = HanimeDefaults.Overlay.onScrim,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
internal fun danmakuStatusText(status: DanmakuStatus): String = when (status) {
    DanmakuStatus.Disabled -> stringResource(Res.string.danmaku_status_off)
    DanmakuStatus.Loading -> stringResource(Res.string.danmaku_status_loading)
    DanmakuStatus.Unmatched -> stringResource(Res.string.danmaku_status_unmatched)
    DanmakuStatus.NoDanmaku -> stringResource(Res.string.danmaku_status_no_danmaku)
    DanmakuStatus.Unavailable -> stringResource(Res.string.danmaku_status_unavailable)
    is DanmakuStatus.CommentOnly -> stringResource(
        Res.string.danmaku_status_comment_only,
        status.commentCount,
    )
    is DanmakuStatus.Linked -> if (status.commentCount > 0) {
        stringResource(
            Res.string.danmaku_status_linked_mixed,
            status.episode.displayTitle(),
            status.remoteCount,
            status.commentCount,
        )
    } else {
        stringResource(
            Res.string.danmaku_status_linked,
            status.episode.displayTitle(),
            status.remoteCount,
        )
    }
}

/** 状态条与选集弹窗共用：弹幕源的集名可能为空，退回作品名。 */
internal fun DanmakuEpisodeRef.displayTitle(): String = episodeTitle.ifBlank { subjectTitle }
