package lovehan1me.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import lovehan1me.data.SettingsRepository
import lovehan1me.data.NetworkRepo
import lovehan1me.core.domain.exception.NotLoggedInException
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.MyListItems
import lovehan1me.core.domain.model.OnlineWatchHistorySort
import lovehan1me.core.domain.state.PageLoadingState
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.ui.viewmodel.CsrfTokenProvider.csrfToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnlineWatchHistoryViewModel : ViewModel() {

    private val _state = MutableStateFlow<PageLoadingState<MyListItems<HanimeInfo>>>(PageLoadingState.Loading)
    val state = _state.asStateFlow()

    private val _items = MutableStateFlow(emptyList<HanimeInfo>())
    val items = _items.asStateFlow()

    private val _selectedSort = MutableStateFlow(OnlineWatchHistorySort.Latest)
    val selectedSort = _selectedSort.asStateFlow()

    private val _loadedPageCount = MutableStateFlow(0)
    val loadedPageCount = _loadedPageCount.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _deleteFlow = MutableSharedFlow<WebsiteState<Boolean>>()
    val deleteFlow = _deleteFlow.asSharedFlow()

    private var isRefreshing = true
    private var isManualRefreshing = false
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            SettingsRepository.loginStateFlow.drop(1).collect { isLoggedIn ->
                if (!isLoggedIn) {
                    clearLoggedOutState()
                }
            }
        }
    }

    fun refresh(sort: OnlineWatchHistorySort = _selectedSort.value) {
        val sortChanged = _selectedSort.value != sort
        _selectedSort.value = sort
        isRefreshing = true
        isManualRefreshing = true
        _isLoadingMore.value = false
        _loadedPageCount.value = 0
        if (sortChanged) {
            _items.value = emptyList()
        }
        _state.value = PageLoadingState.Loading
        loadPage(1)
    }

    fun loadNextPage() {
        if (_state.value is PageLoadingState.Loading || _state.value is PageLoadingState.NoMoreData || _isLoadingMore.value) {
            return
        }
        loadPage(_loadedPageCount.value + 1)
    }

    private fun loadPage(page: Int) {
        val userId = SettingsRepository.savedUserId
        if (!SettingsRepository.isAlreadyLogin || userId.isBlank()) {
            clearLoggedOutState()
            return
        }
        _isLoadingMore.value = !isRefreshing && _items.value.isNotEmpty()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            NetworkRepo.getOnlineWatchHistories(userId, _selectedSort.value, page).collect { pageState ->
                _state.value = pageState
                _items.update { previousItems ->
                    when (pageState) {
                        is PageLoadingState.Success -> {
                            val incoming = pageState.info.hanimeInfo
                            if (incoming.isEmpty()) {
                                _state.value = PageLoadingState.NoMoreData
                            } else {
                                _loadedPageCount.value = page
                            }
                            val baseItems = if (isRefreshing) emptyList() else previousItems
                            isRefreshing = false
                            isManualRefreshing = false
                            _isLoadingMore.value = false
                            (baseItems + incoming).distinctBy(HanimeInfo::videoCode)
                        }

                        is PageLoadingState.Loading -> previousItems

                        else -> {
                            isManualRefreshing = false
                            _isLoadingMore.value = false
                            previousItems
                        }
                    }
                }
            }
        }
    }

    fun isRefreshing(): Boolean = isManualRefreshing

    private fun clearLoggedOutState() {
        loadJob?.cancel()
        loadJob = null
        isRefreshing = false
        isManualRefreshing = false
        _items.value = emptyList()
        _loadedPageCount.value = 0
        _isLoadingMore.value = false
        _state.value = PageLoadingState.Error(
            NotLoggedInException()
        )
    }

    fun deleteItem(item: HanimeInfo) {
        if (!SettingsRepository.isAlreadyLogin || SettingsRepository.savedUserId.isBlank()) {
            clearLoggedOutState()
            return
        }
        val position = _items.value.indexOfFirst { it.videoCode == item.videoCode }
        if (position < 0) return
        viewModelScope.launch {
            NetworkRepo.deleteOnlineWatchHistory(
                videoCode = item.videoCode,
                position = position,
                csrfToken = csrfToken,
            ).collect { state ->
                when (state) {
                    is WebsiteState.Success -> {
                        _items.update { list ->
                            list.toMutableList().apply { removeAt(state.info) }
                        }
                        _deleteFlow.emit(WebsiteState.Success(true))
                    }

                    is WebsiteState.Error -> _deleteFlow.emit(WebsiteState.Error(state.throwable))
                    WebsiteState.Loading -> _deleteFlow.emit(WebsiteState.Loading)
                }
            }
        }
    }
}
