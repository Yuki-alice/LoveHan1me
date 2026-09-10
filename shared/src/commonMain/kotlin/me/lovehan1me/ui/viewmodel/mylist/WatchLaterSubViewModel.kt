package me.lovehan1me.ui.viewmodel.mylist

import me.lovehan1me.logic.SettingsRepository
import me.lovehan1me.logic.NetworkRepo
import me.lovehan1me.logic.model.HanimeInfo
import me.lovehan1me.logic.model.MyListItems
import me.lovehan1me.logic.model.MyListType
import me.lovehan1me.logic.state.PageLoadingState
import me.lovehan1me.logic.state.WebsiteState
import me.lovehan1me.ui.viewmodel.CsrfTokenProvider.csrfToken
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope

class WatchLaterSubViewModel(scope: CoroutineScope) :
    MyListSubViewModel(scope), WatchLaterListController {

    override var watchLaterPage = 1

    override val watchLaterStateFlow: StateFlow<PageLoadingState<MyListItems<HanimeInfo>>> = itemsStateFlow.asStateFlow()
    override val watchLaterFlow: StateFlow<List<HanimeInfo>> = itemsFlow.asStateFlow()

    override fun getMyWatchLaterItems(page: Int) {
        loadItems(MyListType.WATCH_LATER, SettingsRepository.savedUserId, page)
    }

    private val _deleteMyWatchLaterFlow = MutableSharedFlow<WebsiteState<Boolean>>()
    override val deleteMyWatchLaterFlow = _deleteMyWatchLaterFlow.asSharedFlow()

    override fun deleteMyWatchLater(videoCode: String, position: Int) {
        deleteItem(
            deleteCall = {
                NetworkRepo.addToMyList(
                    listCode = "save",
                    videoCode = videoCode,
                    isChecked = false,
                    position = position,
                    csrfToken = csrfToken,
                )
            },
            emitTo = _deleteMyWatchLaterFlow,
            position = position,
            mapState = { state ->
                when (state) {
                    is WebsiteState.Error -> WebsiteState.Error(state.throwable)
                    WebsiteState.Loading -> WebsiteState.Loading
                    is WebsiteState.Success -> WebsiteState.Success(true)
                }
            },
        )
    }

    override fun clearMyListItems() {
        super.clearMyListItems()
        watchLaterPage = 1
    }
}
