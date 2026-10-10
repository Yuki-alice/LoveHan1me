package lovehan1me.feature.home.homepage

import androidx.compose.ui.unit.dp
import lovehan1me.feature.home.homepage.component.bannerHeightFor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Banner 高度三取小门禁（纯函数直测）。
 *
 * 钉住：手机走 16:9 / 宽屏被视口 40% 接管 / 取不到视口时退化 / 超高窗口兜底 440。
 */
class BannerHeightTest {

    @Test
    fun `手机_16比9不受任何封顶影响`() {
        // 400 宽：16:9=225；视口 700*0.4=280 → 225。
        assertEquals(225.dp, bannerHeightFor(400.dp, 700.dp))
    }

    @Test
    fun `宽屏_视口40接管_800高窗口只给320`() {
        // 1100 宽：21:9=471→绝对封顶 440；视口 800*0.4=320 → 320。
        // 这正是桌面 1280x800 首屏只剩 banner 的根因与修法。
        assertEquals(320.dp, bannerHeightFor(1100.dp, 800.dp))
    }

    @Test
    fun `取不到视口_退化为宽高比档加绝对封顶`() {
        assertEquals(440.dp, bannerHeightFor(1100.dp, 0.dp))
    }

    @Test
    fun `超高窗口_440绝对封顶仍起作用`() {
        // 1100 宽 1400 高：视口 560 > 440 → 440。
        assertEquals(440.dp, bannerHeightFor(1100.dp, 1400.dp))
    }

    @Test
    fun `矮窗口_视口封顶可压过下限`() {
        // 1600 宽 600 高：宽高比档 440；视口 240 → 240（视口优先）。
        assertEquals(240.dp, bannerHeightFor(1600.dp, 600.dp))
    }
}
