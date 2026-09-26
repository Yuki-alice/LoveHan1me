package lovehan1me.feature.danmaku

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import lovehan1me.ic_comment
import lovehan1me.ic_settings
import lovehan1me.ui.component.IconButton
import lovehan1me.ui.theme.HanimeDefaults

/**
 * 底栏中间位的弹幕双钮：**开关** + **设置**。
 *
 * 此前这里是状态胶囊（再之前是"点了只提示暂未开放"的假发送框）：
 * 胶囊要说的话搬进设置弹窗的"状况"段，中间位只留动作 ——
 * 开关一眼可见状态（关掉时图标变暗），设置一点即达。
 *
 * 设置钮**不自己弹窗**：底栏随控件自动隐藏被销毁，弹窗挂在这里会被连带销毁。
 * 弹窗改由调用方挂在播放器最上层槽（`VideoPlayerUi` 的 `dialogHost`），
 * 本控件只上报"有人点了设置"。
 *
 * @param session null = 两路全关（功能休眠），此时只剩设置钮（去设置页开）。
 */
@Composable
fun DanmakuControls(
    session: DanmakuSession?,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (session != null) {
            val status by session.status.collectAsStateWithLifecycle()
            val on = status != DanmakuStatus.Disabled
            IconButton(onClick = { session.setEnabled(!on) }) {
                Icon(
                    painter = painterResource(Res.drawable.ic_comment),
                    contentDescription = null,
                    tint = if (on) {
                        HanimeDefaults.Overlay.onScrim
                    } else {
                        HanimeDefaults.Overlay.onScrim.copy(alpha = 0.35f)
                    },
                    modifier = Modifier.size(24.dp),
                )
            }
        }
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
