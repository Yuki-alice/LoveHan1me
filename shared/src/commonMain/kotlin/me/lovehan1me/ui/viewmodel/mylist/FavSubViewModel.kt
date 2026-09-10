package me.lovehan1me.ui.viewmodel.mylist

import me.lovehan1me.logic.SettingsRepository
import me.lovehan1me.logic.NetworkRepo
import me.lovehan1me.logic.model.HanimeInfo
import me.lovehan1me.logic.model.MyListItems
import me.lovehan1me.logic.model.MyListType
import me.lovehan1me.logic.state.PageLoadingState
import me.lovehan1me.logic.state.WebsiteState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope

class FavSubViewModel(scope: CoroutineScope) :
    MyListSubViewModel(scope), FavVideoListController {

    override var favVideoPage = 1
    private var csrfToken: String? = null

    override val favVideoStateFlow: StateFlow<PageLoadingState<MyListItems<HanimeInfo>>> = itemsStateFlow.asStateFlow()
    override val favVideoFlow: StateFlow<List<HanimeInfo>> = itemsFlow.asStateFlow()

    override fun getMyFavVideoItems(userId: String, page: Int) {
        loadItems(MyListType.FAV_VIDEO, userId, page) { csrfToken = it.csrfToken }
    }

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

    override fun clearMyListItems() {
        super.clearMyListItems()
        favVideoPage = 1
    }
}
