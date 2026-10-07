package lovehan1me.feature.library

import lovehan1me.data.SettingsRepository
import lovehan1me.data.NetworkRepo
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.MyListItems
import lovehan1me.core.domain.model.MyListType
import lovehan1me.core.domain.state.PageLoadingState
import lovehan1me.core.domain.state.WebsiteState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope

/**
 * 在线"我喜欢的影片"列表。
 *
 * 分页（判重 / 页号 / 刷新打断）全部由基类 [MyListSubViewModel] 收敛；本类只声明列表类型，
 * 并借 [onPageSuccess] 捕获删除所需的 csrfToken。
 */
class FavSubViewModel(scope: CoroutineScope) :
    MyListSubViewModel(scope, MyListType.FAV_VIDEO), FavVideoListController {

    private var csrfToken: String? = null

    override fun onPageSuccess(info: MyListItems<HanimeInfo>) {
        csrfToken = info.csrfToken
    }

    override val favVideoStateFlow: StateFlow<PageLoadingState<MyListItems<HanimeInfo>>> = itemsStateFlow.asStateFlow()
    override val favVideoFlow: StateFlow<List<HanimeInfo>> = itemsFlow.asStateFlow()

    private val _deleteMyFavVideoFlow = MutableSharedFlow<WebsiteState<Boolean>>()
    override val deleteMyFavVideoFlow = _deleteMyFavVideoFlow.asSharedFlow()

    override fun deleteMyFavVideo(videoCode: String, position: Int) {
        deleteItem(
            deleteCall = {
                NetworkRepo.addToMyFavVideo(
                    videoCode = videoCode,
                    likeStatus = true,
                    currentUserId = SettingsRepository.savedUserId,
                    token = csrfToken,
                )
            },
            emitTo = _deleteMyFavVideoFlow,
            position = position,
            mapState = { it },
        )
    }
}
