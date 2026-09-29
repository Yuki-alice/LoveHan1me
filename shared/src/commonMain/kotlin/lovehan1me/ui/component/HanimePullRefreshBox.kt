package lovehan1me.ui.component

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import lovehan1me.ui.refresh.LocalPageRefreshHub
import lovehan1me.video.player.ui.support.ActiveInputSourceState
import lovehan1me.video.player.ui.support.LocalActiveInputSource

/**
 * 全应用统一的下拉刷新容器。
 *
 * 三件事收在一处，7 个可刷新页面共用：
 *
 * 1. **只认触摸**。Material3 的 `pullToRefresh` 判定触发时只看
 *    `NestedScrollSource.UserInput`（见 `PullToRefreshModifierNode.onPostScroll`），
 *    **不区分指针类型**；而桌面端鼠标滚轮走的 `MouseWheelScrollingLogic` 派发的正是
 *    `UserInput`。两者叠加的后果是：鼠标在列表顶部继续上滚（过卷）会被当成"下拉"。
 *    [TouchOnlyConnection] 在 M3 之前把非触摸输入产生的过卷位移吃掉，桌面端因此不再误触发；
 *    触屏笔记本与平板仍能正常下拉（判定看"最近一次实际用的是什么指针"，不是看平台）。
 * 2. **统一指示器**。项目此前并存两套观感（自绘缩放指示器 vs M3 默认指示器），现统一为
 *    带缓动缩放的 [PullToRefreshDefaults.LoadingIndicator]。
 * 3. **登记全局刷新入口**。把 [onRefresh] 登记到 [LocalPageRefreshHub]，
 *    供桌面快捷键（F5 / Ctrl+R / Cmd+R）派发 —— 桌面端没有下拉手势，刷新必须另有入口。
 *
 * @param isRefreshing 是否正在刷新（驱动指示器）。
 * @param onRefresh 越过阈值时触发；同一动作也会被全局刷新快捷键复用。
 * @param enabled 关掉时既不能下拉、也不会被快捷键派发（用于"首屏未就绪"这类中间态）。
 * @param indicatorTopPadding 指示器距容器顶部的距离。容器顶部若被悬浮顶栏之类的东西盖住，
 *   指示器会被压在下面 —— 顶栏不是本容器的子节点，`zIndex` 管不到它，只能靠让位。
 * @param content 通常是列表；`LazyColumn` 不必自己处理下拉，本容器已经包住。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HanimePullRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: PullToRefreshState = rememberPullToRefreshState(),
    contentAlignment: Alignment = Alignment.TopStart,
    indicatorTopPadding: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val activeInputSource = LocalActiveInputSource.current
    val touchOnlyEnabled by rememberUpdatedState(enabled)
    val connection = remember(activeInputSource) {
        TouchOnlyConnection(
            activeInputSource = activeInputSource,
            enabled = { touchOnlyEnabled },
        )
    }

    val refreshHub = LocalPageRefreshHub.current
    val latestOnRefresh by rememberUpdatedState(onRefresh)
    DisposableEffect(refreshHub) {
        val registration = refreshHub.register { if (touchOnlyEnabled) latestOnRefresh() }
        onDispose { refreshHub.unregister(registration) }
    }

    Box(
        modifier = modifier
            .pullToRefresh(
                state = state,
                isRefreshing = isRefreshing,
                enabled = enabled,
                onRefresh = onRefresh,
            )
            // 顺序不能反：门控要挂在 M3 的**内层**，`onPostScroll` 才会先经过它。
            // 换成先 nestedScroll 再 pullToRefresh，位移会先被 M3 消费掉，门控形同虚设。
            .nestedScroll(connection),
        contentAlignment = contentAlignment,
    ) {
        content()
        PullRefreshIndicator(
            state = state,
            isRefreshing = isRefreshing,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = indicatorTopPadding)
                .zIndex(1f),
        )
    }
}

/**
 * 吃掉非触摸输入产生的向下过卷位移，让 M3 的下拉刷新看不到它。
 *
 * 读 [ActiveInputSourceState.latest] 而不是组合期快照的 `current`：`current` 只在
 * 按下以外的指针事件上提交，而触摸没有 hover —— 用鼠标进页面后的第一次触摸，
 * 按下那一刻 `latest` 已经是 `Touch`，`current` 还没跟上。这里若读 `current`，
 * 那次触摸的下拉会被自己挡掉。
 *
 * 只在 `available.y > 0` 时消费：负方向是列表自身的滚动余量，拦下来等于列表滚不动。
 */
private class TouchOnlyConnection(
    private val activeInputSource: ActiveInputSourceState,
    private val enabled: () -> Boolean,
) : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (!enabled()) return Offset.Zero
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        if (activeInputSource.latest == PointerType.Touch) return Offset.Zero
        if (available.y <= 0f) return Offset.Zero
        return Offset(0f, available.y)
    }
}

/**
 * 下拉指示器：距离分数经缓动映射为缩放，避免"刚拉一点点就蹦出整个进度圈"。
 *
 * 刷新期间恒为 1，不跟随手指 —— 松手后 `distanceFraction` 会回落到阈值附近，
 * 跟着它缩放会让指示器在刷新过程中缩一下。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PullRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val scaleFraction = if (isRefreshing) {
        1f
    } else {
        LinearOutSlowInEasing.transform(state.distanceFraction).coerceIn(0f, 1f)
    }

    if (isRefreshing || scaleFraction > 0f) {
        Box(
            modifier = modifier.graphicsLayer {
                scaleX = scaleFraction
                scaleY = scaleFraction
            },
        ) {
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = isRefreshing,
            )
        }
    }
}
