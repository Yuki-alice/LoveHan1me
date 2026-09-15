package lovehan1me.feature.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.Res
import lovehan1me.comment
import lovehan1me.introduction
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.data.SettingsRepository
import lovehan1me.core.domain.state.VideoLoadingState
import lovehan1me.app.bridge.VideoPageHost
import lovehan1me.feature.video.CommentViewModel
import lovehan1me.feature.video.VideoViewModel

@Composable
fun VideoRouteContent(
    videoCode: String,
    videoState: VideoLoadingState<*>,
    videoViewModel: VideoViewModel,
    commentViewModel: CommentViewModel,
    fromDownload: Boolean,
    pendingDownloadPrompt: DownloadPromptState?,
    onPendingDownloadPromptChange: (DownloadPromptState?) -> Unit,
    onRetry: () -> Unit,
    onOpenVideo: (HanimeInfo) -> Unit,
    onOpenArtist: (lovehan1me.core.domain.model.HanimeVideo.Artist) -> Unit,
    onNavigateToSearch: (String) -> Unit,
    onToggleSubscribe: (lovehan1me.core.domain.model.HanimeVideo.Artist) -> Unit,
    onToggleFavorite: (lovehan1me.core.domain.model.HanimeVideo) -> Unit,
    onRequestManageMyList: (() -> Unit) -> Unit,
    onRateVideo: (lovehan1me.core.domain.model.HanimeVideo, Boolean) -> Unit,
    onManageMyList: (lovehan1me.core.domain.model.HanimeVideo.MyList?, List<Boolean>) -> Unit,
    onQuickCheckIn: (lovehan1me.data.database.entity.CheckInRecordEntity) -> Unit,
    onPrepareDownload: (String, lovehan1me.core.domain.model.HanimeVideo?) -> Unit,
    onConfirmDownloadPrompt: (lovehan1me.core.domain.model.HanimeVideo?, Boolean) -> Unit,
    onRequestOpenOfficialDownloadPage: () -> Unit,
    onOpenWebPage: () -> Unit,
    onOpenOriginalComic: (String) -> Unit,
    onOpenShare: (String, String) -> Unit,
    onCopyText: (String) -> Unit,
    onIntroductionLinkClick: (String) -> Unit,
    stringLongPressShare: String,
    pageHost: VideoPageHost,
    /**
     * Animeko 宽屏右栏：详情（全量简介：标题收藏钮 + 底部相关推荐）｜评论。
     * 窄屏保持 false，走原简介/评论双 Tab。
     * 配色**跟随设置的主题**，不做强制深色（弹幕输入条在播放器底栏，不在这里）。
     */
    wideRail: Boolean = false,
) {
    val hostUiState by videoViewModel.videoHostUiStateFlow.collectAsStateWithLifecycle()
    val settings by SettingsRepository.settings.collectAsStateWithLifecycle()
    val disableComments = settings.disableComments
    val tabs = remember(disableComments, hostUiState.commentBadgeCount, fromDownload) {
        buildList {
            add(VideoTabItem(Res.string.introduction))
            if (!fromDownload && !disableComments) {
                add(VideoTabItem(Res.string.comment, badgeCount = hostUiState.commentBadgeCount))
            }
        }
    }

    // 简介页只有一处实现：窄屏 Tab 页 0 与宽屏右栏详情页共用，改只改这里。
    @Composable
    fun IntroPage(showTitleFavorite: Boolean = false) {
        RenderVideoIntroductionContent(
            videoCode = videoCode,
            viewModel = videoViewModel,
            pendingDownloadPrompt = pendingDownloadPrompt,
            onPendingDownloadPromptChange = onPendingDownloadPromptChange,
            onOpenVideo = onOpenVideo,
            onOpenArtist = onOpenArtist,
            onNavigateToSearch = onNavigateToSearch,
            onToggleSubscribe = onToggleSubscribe,
            onToggleFavorite = onToggleFavorite,
            onRequestManageMyList = onRequestManageMyList,
            onRateVideo = onRateVideo,
            onManageMyList = onManageMyList,
            onQuickCheckIn = onQuickCheckIn,
            onPrepareDownload = onPrepareDownload,
            onConfirmDownloadPrompt = onConfirmDownloadPrompt,
            onRequestOpenOfficialDownloadPage = onRequestOpenOfficialDownloadPage,
            onOpenWebPage = onOpenWebPage,
            onOpenOriginalComic = onOpenOriginalComic,
            onOpenShare = onOpenShare,
            onCopyText = onCopyText,
            onIntroductionLinkClick = onIntroductionLinkClick,
            stringLongPressShare = stringLongPressShare,
            showTitleFavorite = showTitleFavorite,
        )
    }

    @Composable
    fun CommentPage() {
        RenderVideoCommentContent(
            videoCode = videoCode,
            viewModel = commentViewModel,
            reportMessages = remember { kotlinx.coroutines.flow.MutableSharedFlow() },
            getMessageText = { message ->
                // P6c：VM Message 已携带文本（commonMain 无 R-int）
                message.text
            },
            pageHost = pageHost,
        )
    }

    @Composable
    fun DetailsTabs(showTitleFavorite: Boolean = false) {
        VideoTabsContent(
            tabs = tabs,
            selectedTabIndex = hostUiState.selectedTabIndex,
            onSelectedTabChange = { videoViewModel.setSelectedTabIndex(videoCode, it) },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            if (page == 0) {
                IntroPage(showTitleFavorite = showTitleFavorite)
            } else {
                CommentPage()
            }
        }
    }

    VideoScreen(
        state = videoState,
        onRetry = onRetry,
    ) {
        if (wideRail) {
            // 宽屏右栏：详情（标题收藏钮）｜评论。配色**跟随设置的主题**——
            // 原先这里整体套 HanimeTheme(darkTheme = true) 强制沉浸黑，浅色模式下
            // 右栏仍是深色、与「跟随主题」的预期相悖，已移除（连同下层两处重复包裹）。
            // 弹幕输入条在播放器底栏（Kazumi 布局），不挂在右栏 tab 顶上。
            DetailsTabs(showTitleFavorite = true)
            return@VideoScreen
        }
        DetailsTabs()
    }
}
