package lovehan1me.ui.screen.home.videogrid

import androidx.compose.foundation.lazy.grid.LazyGridState
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.state.PageLoadingState

/**
 * 判断视频网格是否需要加载更多。
 *
 * @param items 当前视频列表
 * @param state 加载状态
 * @return 是否需要触发加载更多
 */
fun LazyGridState.canLoadMore(
    items: List<HanimeInfo>,
    state: PageLoadingState<*>,
): Boolean {
    if (items.isEmpty()) return false
    if (state is PageLoadingState.Loading || state is PageLoadingState.NoMoreData || state is PageLoadingState.Error) {
        return false
    }
    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return false
    return lastVisible >= layoutInfo.totalItemsCount - 4
}
