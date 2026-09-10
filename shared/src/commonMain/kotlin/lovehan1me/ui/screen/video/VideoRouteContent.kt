package lovehan1me.ui.screen.video

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lovehan1me.Res
import lovehan1me.comment
import lovehan1me.introduction
import lovehan1me.logic.model.HanimeInfo
import lovehan1me.logic.SettingsRepository
import lovehan1me.logic.state.VideoLoadingState
import lovehan1me.ui.bridge.VideoPageHost
import lovehan1me.ui.viewmodel.CommentViewModel
import lovehan1me.ui.viewmodel.VideoViewModel

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
    onOpenArtist: (lovehan1me.logic.model.HanimeVideo.Artist) -> Unit,
    onNavigateToSearch: (String) -> Unit,
    onToggleSubscribe: (lovehan1me.logic.model.HanimeVideo.Artist) -> Unit,
    onToggleFavorite: (lovehan1me.logic.model.HanimeVideo) -> Unit,
    onRequestManageMyList: (() -> Unit) -> Unit,
    onRateVideo: (lovehan1me.logic.model.HanimeVideo, Boolean) -> Unit,
    onManageMyList: (lovehan1me.logic.model.HanimeVideo.MyList?, List<Boolean>) -> Unit,
    onQuickCheckIn: (lovehan1me.logic.entity.CheckInRecordEntity) -> Unit,
    onPrepareDownload: (String, lovehan1me.logic.model.HanimeVideo?) -> Unit,
    onConfirmDownloadPrompt: (lovehan1me.logic.model.HanimeVideo?, Boolean) -> Unit,
    onRequestOpenOfficialDownloadPage: () -> Unit,
    onOpenWebPage: () -> Unit,
    onOpenOriginalComic: (String) -> Unit,
    onOpenShare: (String, String) -> Unit,
    onCopyText: (String) -> Unit,
    onIntroductionLinkClick: (String) -> Unit,
    stringLongPressShare: String,
    pageHost: VideoPageHost,
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

    VideoScreen(
        state = videoState,
        onRetry = onRetry,
    ) {
        VideoTabsContent(
            tabs = tabs,
            selectedTabIndex = hostUiState.selectedTabIndex,
            onSelectedTabChange = { videoViewModel.setSelectedTabIndex(videoCode, it) },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            if (page == 0) {
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
                )
            } else {
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
        }
    }
}
