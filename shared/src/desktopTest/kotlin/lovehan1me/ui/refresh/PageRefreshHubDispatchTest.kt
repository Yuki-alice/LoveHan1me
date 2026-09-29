package lovehan1me.ui.refresh

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import lovehan1me.ui.component.HanimePullRefreshBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 全局刷新入口（桌面 F5 / Ctrl+R / Cmd+R）的中转行为。
 *
 * 跑法：`:shared:desktopTest --tests "lovehan1me.ui.refresh.PageRefreshHubDispatchTest"`
 *
 * 关键语义是**最后组合者优先**：导航转场期间相邻两条路由会同时处于组合中，
 * 只有栈顶那一页该被刷新；栈顶退出后要能回落到下面那一页，而不是把登记一起清空。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTestApi::class)
class PageRefreshHubDispatchTest {

    @Test
    fun `派发到后组合的那一页_并在其退出后回落`() = runComposeUiTest {
        val hub = PageRefreshHub()
        var bottomComposed by mutableStateOf(true)
        var topComposed by mutableStateOf(true)
        var bottomRefreshes by mutableIntStateOf(0)
        var topRefreshes by mutableIntStateOf(0)

        setContent {
            CompositionLocalProvider(LocalPageRefreshHub provides hub) {
                if (bottomComposed) {
                    HanimePullRefreshBox(
                        isRefreshing = false,
                        onRefresh = { bottomRefreshes++ },
                        modifier = Modifier.size(100.dp),
                    ) {
                        Spacer(Modifier.height(1.dp))
                    }
                }
                if (topComposed) {
                    HanimePullRefreshBox(
                        isRefreshing = false,
                        onRefresh = { topRefreshes++ },
                        modifier = Modifier.size(100.dp),
                    ) {
                        Spacer(Modifier.height(1.dp))
                    }
                }
            }
        }

        runOnIdle {
            assertTrue(hub.refresh())
            assertEquals(1, topRefreshes)
            assertEquals(0, bottomRefreshes)
        }

        // 栈顶退出 → 回落到下面那一页，登记不能一起消失
        topComposed = false
        runOnIdle {
            assertTrue(hub.refresh())
            assertEquals(1, topRefreshes)
            assertEquals(1, bottomRefreshes)
        }

        // 全部退出 → 没有可派发的动作，调用方据此不消费按键
        bottomComposed = false
        runOnIdle { assertFalse(hub.refresh()) }
    }
}
