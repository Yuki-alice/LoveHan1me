package lovehan1me.feature.library

import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.MyListItems
import lovehan1me.core.domain.state.PageLoadingState
import lovehan1me.core.domain.state.WebsiteState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * MyList 系列分页列表（收藏 / 稍后再看）的公共契约。
 *
 * **分页序号不再对外暴露**：[loadNextPage] / [refresh] 是仅有的两个分页入口，页号的读取与推进
 * （判重、自增、作废在途）全部收在实现里。旧接口暴露 `var favVideoPage` 让 UI 自己"先读 page、
 * 再自增"，正是重复请求 / 跳页的来源（详见 `MyListPaging.kt`）。
 */
interface MyListPagingController {
    /** 已加载的页数。 */
    val loadedPageCount: StateFlow<Int>

    /** 是否正在加载更多（用于 UI 的加载页脚）。 */
    val isLoadingMore: StateFlow<Boolean>

    /** 清空列表并把分页序列归零（同时作废在途请求）。 */
    fun clearMyListItems()

    /**
     * 加载下一页。
     *
     * 判重与页号推进都在实现内完成（见 `MyListPaging.nextPageOrNull`）。
     *
     * @return 本次是否真的发起；`false` = 已有在途请求（判重丢弃）。
     */
    fun loadNextPage(): Boolean

    /** 下拉刷新：作废在途请求，从第一页重新加载（"是否刷新"由本轮凭证表达，不再是共享 bool）。 */
    fun refresh()
}

/**
 * 稍后再看列表的在线/本地共用接口，供路由层按登录状态切换实现。
 */
interface WatchLaterListController : MyListPagingController {
    val watchLaterStateFlow: StateFlow<PageLoadingState<MyListItems<HanimeInfo>>>
    val watchLaterFlow: StateFlow<List<HanimeInfo>>
    val deleteMyWatchLaterFlow: SharedFlow<WebsiteState<Boolean>>

    fun deleteMyWatchLater(videoCode: String, position: Int)
}

/**
 * 我喜欢的影片列表的在线/本地共用接口，供路由层按登录状态切换实现。
 */
interface FavVideoListController : MyListPagingController {
    val favVideoStateFlow: StateFlow<PageLoadingState<MyListItems<HanimeInfo>>>
    val favVideoFlow: StateFlow<List<HanimeInfo>>
    val deleteMyFavVideoFlow: SharedFlow<WebsiteState<Boolean>>

    fun deleteMyFavVideo(videoCode: String, position: Int)
}
