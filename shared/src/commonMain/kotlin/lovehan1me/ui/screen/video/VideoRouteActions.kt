package lovehan1me.ui.screen.video

import lovehan1me.logic.DatabaseRepo
import lovehan1me.logic.SettingsRepository
import lovehan1me.Res
import lovehan1me.checkin_success
import lovehan1me.copy_to_clipboard
import lovehan1me.fault_prompt
import lovehan1me.getHanimeVideoDownloadLink
import lovehan1me.login_first
import lovehan1me.no_video_links_found
import lovehan1me.getHanimeVideoLink
import lovehan1me.logic.dao.Han1meDatabases
import lovehan1me.logic.entity.CheckInRecordEntity
import lovehan1me.logic.entity.download.DownloadGroupEntity
import lovehan1me.logic.model.HanimeVideo
import lovehan1me.logic.model.SearchOption
import lovehan1me.ui.navigation.main.SearchRoute
import lovehan1me.ui.viewmodel.VideoViewModel
import lovehan1me.ui.viewmodel.updateCheckInWidget
import lovehan1me.logic.ioDispatcher
import lovehan1me.utils.SonnerToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString

/**
 * M3：自 `:app` 下沉（视频页事件胶水）。
 *
 * 与 `:app` 版的差异：
 * - `context: Context` 删除：`openArtistSearch/openTagSearch` 改经
 *   [onOpenSearchRoute] 回调（调用方入栈 `SearchRoute`；高级搜索参数直接拼
 *   `Map<String, String>`，不再经 `HAdvancedSearch` + `Serializable` 中转）；
 * - `quickCheckIn` 改共享入口（`Han1meDatabases.checkInRecord` +
 *   `updateCheckInWidget`；原 Glance `updateAll(context)`）；
 * - 下载落盘（`HCacheManager` + `HanimeDownloadManager/Worker`，P7）改经
 *   [onEnqueueDownload] 回调，见 [EnqueueDownloadRequest]。
 */
data class EnqueueDownloadRequest(
    val video: HanimeVideo,
    val videoCode: String,
    val quality: String?,
    val groupId: Int,
    val redownload: Boolean,
)

class VideoRouteActions(
    private val scope: CoroutineScope,
    private val viewModel: VideoViewModel,
    private val genres: List<SearchOption>,
    private val onPendingDownloadPromptChange: (DownloadPromptState?) -> Unit,
    private val getCheckedQuality: () -> String?,
    private val setCheckedQuality: (String?) -> Unit,
    private val onOpenUri: (String) -> Unit,
    private val onCopyText: (String) -> Unit,
    private val onOpenSearchRoute: (SearchRoute) -> Unit,
    private val onRequestUnsubscribe: (HanimeVideo.Artist) -> Unit,
    private val onRequestNotificationPermission: () -> Unit,
    private val onRequestLocalListAction: (() -> Unit) -> Unit,
    private val onEnqueueDownload: (EnqueueDownloadRequest) -> Unit,
) {
    fun openArtistSearch(artist: HanimeVideo.Artist) {
        val searchKey = genres.firstOrNull { option ->
            option.lang?.let { lang ->
                artist.genre == lang.zhrCN ||
                        artist.genre == lang.zhrTW ||
                        artist.genre == lang.en
            } == true
        }?.searchKey ?: ""
        val routeMap = buildMap {
            put("QUERY", artist.name)
            if (searchKey.isNotEmpty() && !SettingsRepository.searchArtistIgnoreVideoType) {
                put("GENRE", searchKey)
            }
        }
        onOpenSearchRoute(
            SearchRoute(query = artist.name, advancedSearchJson = Json.encodeToString(routeMap))
        )
    }

    fun openTagSearch(tag: String) {
        onOpenSearchRoute(SearchRoute(query = tag))
    }

    fun toggleArtistSubscription(artist: HanimeVideo.Artist) {
        val post = artist.post ?: return
        if (!SettingsRepository.isAlreadyLogin) {
            scope.launch { SonnerToast.warning(getString(Res.string.login_first)) }
            return
        }
        if (artist.isSubscribed) {
            onRequestUnsubscribe(artist)
        } else {
            viewModel.subscribeArtist(post.userId, post.artistId)
        }
    }

    fun confirmUnsubscribe(artist: HanimeVideo.Artist) {
        val post = artist.post ?: return
        viewModel.unsubscribeArtist(post.userId, post.artistId)
    }

    fun toggleFavorite(video: HanimeVideo) {
        if (!SettingsRepository.isAlreadyLogin) {
            onRequestLocalListAction(viewModel::toggleLocalFavorite)
            return
        }
        if (video.isFav) {
            viewModel.removeFromFavVideo(viewModel.videoCode, video.currentUserId)
        } else {
            viewModel.addToFavVideo(viewModel.videoCode, video.currentUserId)
        }
    }

    fun rateVideo(video: HanimeVideo, isPositive: Boolean) {
        if (!SettingsRepository.isAlreadyLogin) {
            scope.launch { SonnerToast.warning(getString(Res.string.login_first)) }
            return
        }
        viewModel.rateVideo(video, isPositive)
    }

    fun updateMyListSelection(
        myList: HanimeVideo.MyList?,
        selectedStates: List<Boolean>,
    ) {
        if (!SettingsRepository.isAlreadyLogin) {
            val localMyList = myList
            if (localMyList != null && localMyList.myListInfo.isNotEmpty()) {
                viewModel.updateLocalMyListSelection(localMyList, selectedStates)
            }
            return
        }
        if (myList == null || myList.myListInfo.isEmpty()) {
            scope.launch { SonnerToast.warning(getString(Res.string.login_first)) }
            return
        }
        myList.myListInfo.forEachIndexed { index, info ->
            val newChecked = selectedStates.getOrNull(index) ?: return@forEachIndexed
            if (info.isSelected != newChecked) {
                viewModel.modifyMyList(
                    listCode = info.code,
                    videoCode = viewModel.videoCode,
                    isChecked = newChecked,
                    position = index,
                )
            }
        }
    }

    fun quickCheckIn(record: CheckInRecordEntity) {
        scope.launch(ioDispatcher) {
            Han1meDatabases.checkInRecord.checkInDao().insert(record)
            runCatching { updateCheckInWidget() }
            withContext(Dispatchers.Main) {
                SonnerToast.success(getString(Res.string.checkin_success))
            }
        }
    }

    fun openIntroductionLink(link: String) {
        try {
            onOpenUri(link)
        } catch (_: Exception) {
            onCopyText(link)
            scope.launch { SonnerToast.success(getString(Res.string.copy_to_clipboard)) }
        }
    }

    fun openOriginalComic(comicLink: String) {
        runCatching { onOpenUri(comicLink) }
            .onFailure { scope.launch { SonnerToast.error(getString(Res.string.fault_prompt)) } }
    }

    fun openVideoWebPage() {
        onOpenUri(getHanimeVideoLink(viewModel.videoCode))
    }

    fun openOfficialDownloadPage() {
        onOpenUri(getHanimeVideoDownloadLink(viewModel.videoCode))
    }

    fun startDownloadFlow(videoData: HanimeVideo) {
        if (videoData.videoUrls.isEmpty()) {
            scope.launch { SonnerToast.warning(getString(Res.string.no_video_links_found)) }
            return
        }
        viewModel.findDownloadedHanime(viewModel.videoCode)
    }

    fun confirmPendingDownload(
        videoData: HanimeVideo,
        pendingDownloadPrompt: DownloadPromptState?,
        autoCreateGroup: Boolean,
    ) {
        // 下载确认即请求通知权限（Android 13+；拒绝不影响入队，系统侧去重）。
        onRequestNotificationPermission()
        val redownload = pendingDownloadPrompt?.oldQuality != null
        onPendingDownloadPromptChange(null)
        scope.launch {
            val groupName = videoData.downloadGroupName()
            val groupId = if (autoCreateGroup && groupName.isNotEmpty()) {
                withContext(ioDispatcher) {
                    DatabaseRepo.HanimeDownload.getOrCreateGroup(groupName)
                }
            } else {
                pendingDownloadPrompt?.oldGroupId ?: DownloadGroupEntity.DEFAULT_GROUP_ID
            }
            onEnqueueDownload(
                EnqueueDownloadRequest(
                    video = videoData,
                    videoCode = viewModel.videoCode,
                    quality = getCheckedQuality(),
                    groupId = groupId,
                    redownload = redownload,
                )
            )
        }
    }

    private fun HanimeVideo.downloadGroupName(): String =
        sequenceOf(playlist?.playlistName, chineseTitle, title)
            .firstNotNullOfOrNull { candidate -> candidate?.trim()?.takeIf(String::isNotEmpty) }
            .orEmpty()
}
