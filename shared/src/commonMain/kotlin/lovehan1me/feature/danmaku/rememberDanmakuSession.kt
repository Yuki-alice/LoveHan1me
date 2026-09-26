package lovehan1me.feature.danmaku

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import lovehan1me.core.domain.model.VideoComments
import lovehan1me.data.SettingsRepository
import lovehan1me.data.danmaku.DanmakuProvider
import lovehan1me.data.danmaku.EmptyDanmakuProvider
import lovehan1me.feature.player.PlaybackSessionState
import lovehan1me.feature.player.PlaybackPhase

/**
 * 播放器页面的弹幕持有者。
 *
 * 休眠条件跟着主源走：弹弹未配置 **且** 评论投影也关了，才返回 null、下游一切都不存在。
 * 弹弹未配置但评论开着时，用空桩 provider 撑起 session —— 评论照飘，
 * 弹弹那路恒落空（状态条说评论，选集弹窗搜出空结果）。
 * 评论开关关了但弹弹配了时，行为与以前完全一致。
 *
 * 为什么不塞进 ViewModel：本仓的既有做法是「纯逻辑类 + 屏幕持有」
 * （`PlaybackController` / [PlaybackStallDetector][lovehan1me.feature.player.PlaybackStallDetector] 同理），
 * 弹幕对播放器是**旁路**，它挂了不该牵连播放，换片子时也不该跟着重建整个 ViewModel。
 *
 * 休眠判定只看 [DanmakuProvider.from]（代理与凭据都没配 → null），
 * **不看**用户的总开关：状态条要能分辨「未配置·去设置」和「已配置·已关闭」。
 */
@Composable
fun rememberDanmakuSession(
    videoCode: String,
    title: String,
    playbackState: PlaybackSessionState?,
    comments: List<VideoComments.VideoComment> = emptyList(),
): DanmakuSession? {
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()

    // 接入方式（代理 / 凭据）变了就重建数据源；共用的那个 HttpClient 是 lazy 的，重建很轻。
    val provider = remember(settings.danmakuProxyBase, settings.danmakuAppId, settings.danmakuAppSecret) {
        DanmakuProvider.from(settings)
    }
    if (provider == null && !settings.danmakuCommentEnabled) return null

    // 作用域在这里建、也在这里收：session 只是被注入了一个作用域，
    // 由它去 cancel 别人的 scope 是错的。
    val (session, scope) = remember(videoCode, title, provider) {
        val created = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        DanmakuSession(
            videoCode = videoCode,
            title = title,
            provider = provider ?: EmptyDanmakuProvider,
            scope = created,
        ) to created
    }

    DisposableEffect(session) {
        onDispose {
            session.dispose()
            scope.cancel()
        }
    }

    LaunchedEffect(session, settings.danmakuEnabled) {
        session.setEnabled(settings.danmakuEnabled)
    }

    LaunchedEffect(session, settings.danmakuCommentEnabled) {
        session.setCommentEnabled(settings.danmakuCommentEnabled)
    }

    LaunchedEffect(
        session,
        settings.danmakuShowScroll,
        settings.danmakuShowTop,
        settings.danmakuShowBottom,
    ) {
        session.setLocationVisibility(
            scroll = settings.danmakuShowScroll,
            top = settings.danmakuShowTop,
            bottom = settings.danmakuShowBottom,
        )
    }

    LaunchedEffect(session, comments) {
        session.setComments(comments)
    }

    // 位置与播放态的唯一入口。每次状态推送（Android 250ms / iOS 500ms / 桌面事件驱动）
    // 喂一次快照；[PlaybackPhase.Ready] 之外视为"位置失去参考意义"，整组丢掉重来。
    LaunchedEffect(playbackState) {
        val state = playbackState ?: return@LaunchedEffect
        val engine = state.engine
        session.onPlaybackSnapshot(
            positionMs = engine.positionMs,
            durationMs = engine.durationMs,
            playbackSpeed = engine.playbackSpeed,
            frozen = !engine.isPlaying || engine.isBuffering ||
                state.isStalled || state.isSwitchingQuality,
            reset = engine.phase != PlaybackPhase.Ready,
        )
    }

    return session
}
