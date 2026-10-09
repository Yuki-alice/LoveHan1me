package lovehan1me.ui.component

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** 首页轮播默认翻页间隔（对齐 misaka 上游；文字为主可自行加长）。 */
val PagerAutoScrollInterval = 5.seconds

/**
 * Pager 自动翻页（misaka `AutoScrollEffect` 移植 + animeko `CarouselAutoAdvanceEffect` 补强）。
 *
 * - 页码只在协程内读：`animateScrollToPage` 起步即改 `currentPage`，读进组合期当 key
 *   会被自己的重组取消而卡在两页之间；
 * - 拖动中取消计时，后台暂停（`repeatOnLifecycle(RESUMED)`），单页/预览不滚；
 * - 用户滑动或外部切页后重新完整计时（`settledPage`）；
 * - 桌面 hover 暂停由调用方经 [enabled] 传入（`!isHovered`，见 animeko 趋势轮播），
 *   此处不直接依赖 hover，避免移动端无谓重组。
 */
@Composable
fun PagerAutoScrollEffect(
    pagerState: PagerState,
    pageCount: Int,
    interval: Duration = PagerAutoScrollInterval,
    enabled: Boolean = true,
) {
    if (LocalInspectionMode.current || pageCount <= 1 || !enabled) return

    val lifecycleOwner = LocalLifecycleOwner.current
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(pagerState, pageCount, isDragged, interval, enabled) {
        if (isDragged || !enabled) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var settledPage = pagerState.currentPage
            while (true) {
                delay(interval)
                if (pagerState.isScrollInProgress) continue
                if (pagerState.currentPage != settledPage) {
                    settledPage = pagerState.currentPage
                    continue
                }
                pagerState.animateScrollToPage((settledPage + 1) % pageCount)
                settledPage = pagerState.currentPage
            }
        }
    }
}
