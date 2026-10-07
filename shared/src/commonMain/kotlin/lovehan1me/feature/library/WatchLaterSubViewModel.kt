package lovehan1me.feature.library

import lovehan1me.data.NetworkRepo
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.MyListItems
import lovehan1me.core.domain.model.MyListType
import lovehan1me.core.domain.state.PageLoadingState
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.data.network.CsrfTokenProvider.csrfToken
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope

/**
 * 在线"稍后再看"列表。
 *
 * 分页（判重 / 页号 / 刷新打断）全部由基类 [MyListSubViewModel] 收敛；本类只声明列表类型。
 */
class WatchLaterSubViewModel(scope: CoroutineScope) :
    MyListSubViewModel(scope, MyListType.WATCH_LATER), WatchLaterListController {

    override val watchLaterStateFlow: StateFlow<PageLoadingState<MyListItems<HanimeInfo>>> = itemsStateFlow.asStateFlow()
    override val watchLaterFlow: StateFlow<List<HanimeInfo>> = itemsFlow.asStateFlow()

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
}
