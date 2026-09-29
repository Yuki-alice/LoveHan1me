package lovehan1me.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import lovehan1me.ui.refresh.LocalPageRefreshHub
import lovehan1me.ui.refresh.PageRefreshHub
import lovehan1me.video.player.ui.support.ActiveInputSourceState
import lovehan1me.video.player.ui.support.LocalActiveInputSource
import lovehan1me.video.player.ui.support.trackActiveInputSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * [HanimePullRefreshBox] 的输入门控回归。
 *
 * 跑法：`:shared:desktopTest --tests "lovehan1me.ui.component.HanimePullRefreshInputTest"`
 *
 * 为什么必须真发指针事件：门控的判定依据是"最近一次实际用了什么指针"，
 * 这个值只在指针事件流里才会被写。渲染类测试（ImageComposeScene）看不到这条链路。
 *
 * **`鼠标滚轮...` 那条用例的输入形态是量出来的，不能随手改**（2026-09-29 实测，方法见
 * `docs/evidence/2026-09-29-下拉刷新指针类型门控.md`）：桌面滚轮只有在
 * 「列表本身有滚动余量、同一次滚动把余量用光后继续上推」时，才会把过卷位移交给父级连接，
 * 也只有这种形态会把指示器拉出来（门控关闭时 `distanceFraction` 实测 2.0）。
 * 换成"内容不可滚直接上推"或"下推"，无论有没有门控都是 0.0 —— 那种写法是空用例。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTestApi::class)
class HanimePullRefreshInputTest {

    @Test
    fun `滚轮在列表滚到顶部后继续上推不会拉出下拉指示器`() = runComposeUiTest {
        val inputSource = ActiveInputSourceState()
        lateinit var pullState: PullToRefreshState
        var refreshes by mutableIntStateOf(0)

        setContent {
            CompositionLocalProvider(LocalActiveInputSource provides inputSource) {
                pullState = rememberPullToRefreshState()
                HanimePullRefreshBox(
                    isRefreshing = false,
                    onRefresh = { refreshes++ },
                    state = pullState,
                    modifier = Modifier
                        .size(320.dp)
                        .trackActiveInputSource(inputSource)
                        .testTag("pull"),
                ) {
                    // 内容比视口高、且起始位置不在顶部：否则滚轮过卷到不了父级连接
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState(initial = 300)),
                    ) {
                        Spacer(Modifier.height(1_000.dp))
                    }
                }
            }
        }

        onNodeWithTag("pull").performMouseInput {
            moveTo(center)
            scroll(-5_000f)
        }
        runOnIdle {
            // 关键断言：指示器不许被拉出来。refreshes 在门控缺失时也仍是 0
            // （滚轮不产生抬手/fling，M3 不会真的发出刷新），所以它只是陪跑。
            assertEquals(0f, pullState.distanceFraction)
            assertEquals(0, refreshes)
        }
    }

    @Test
    fun `触摸下拉仍然会触发刷新`() = runComposeUiTest {
        val inputSource = ActiveInputSourceState()
        var refreshes by mutableIntStateOf(0)

        setContent {
            CompositionLocalProvider(LocalActiveInputSource provides inputSource) {
                HanimePullRefreshBox(
                    isRefreshing = false,
                    onRefresh = { refreshes++ },
                    modifier = Modifier
                        .size(320.dp)
                        .trackActiveInputSource(inputSource)
                        .testTag("pull"),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Spacer(Modifier.height(1.dp))
                    }
                }
            }
        }

        onNodeWithTag("pull").performTouchInput {
            swipe(
                start = topCenter + Offset(0f, 20f),
                end = bottomCenter - Offset(0f, 20f),
                durationMillis = 1_000,
            )
        }
        runOnIdle { assertEquals(1, refreshes) }
    }

    @Test
    fun `门控不会拦住子级的鼠标拖动`() = runComposeUiTest {
        val inputSource = ActiveInputSourceState()
        var dragged by mutableFloatStateOf(0f)

        setContent {
            CompositionLocalProvider(LocalActiveInputSource provides inputSource) {
                HanimePullRefreshBox(
                    isRefreshing = false,
                    onRefresh = {},
                    modifier = Modifier
                        .size(320.dp)
                        .trackActiveInputSource(inputSource),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .testTag("child")
                            .draggable(
                                rememberDraggableState { dragged += it },
                                Orientation.Horizontal,
                            ),
                    )
                }
            }
        }

        onNodeWithTag("child").performMouseInput {
            moveTo(centerLeft)
            press()
            moveTo(centerRight)
            release()
        }
        runOnIdle { assertNotEquals(0f, dragged) }
    }

    @Test
    fun `门控不会拦住正常的鼠标滚动`() = runComposeUiTest {
        val inputSource = ActiveInputSourceState()
        lateinit var scrollState: ScrollState

        setContent {
            CompositionLocalProvider(LocalActiveInputSource provides inputSource) {
                scrollState = rememberScrollState(initial = 200)
                HanimePullRefreshBox(
                    isRefreshing = false,
                    onRefresh = {},
                    modifier = Modifier
                        .size(320.dp)
                        .trackActiveInputSource(inputSource)
                        .testTag("pull"),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                    ) {
                        Spacer(Modifier.height(2_000.dp))
                    }
                }
            }
        }

        onNodeWithTag("pull").performMouseInput {
            moveTo(center)
            scroll(100f)
        }
        runOnIdle { assertNotEquals(200, scrollState.value) }
    }
}
