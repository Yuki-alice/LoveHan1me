package lovehan1me.ui.theme

import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.ContrastLevel
import lovehan1me.core.domain.model.ThemeConfig
import lovehan1me.core.domain.model.ThemeMode
import lovehan1me.core.domain.model.themeConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

// B4 守卫：主题派生映射必须只含四量，且无关字段变化不得改变去重键。
class ThemeConfigTest {

    @Test
    fun `默认设置映射到默认主题配置`() {
        assertEquals(ThemeConfig(), AppSettings().themeConfig())
    }

    @Test
    fun `四量各自流向主题配置`() {
        val config = AppSettings(
            themeMode = ThemeMode.Dark,
            themeId = "midnight",
            contrastLevel = ContrastLevel.High,
            amoled = true,
        ).themeConfig()
        assertEquals(ThemeMode.Dark, config.themeMode)
        assertEquals("midnight", config.themeId)
        assertEquals(ContrastLevel.High, config.contrastLevel)
        assertEquals(true, config.amoled)
    }

    @Test
    fun `改弹幕字号不改变主题去重键`() {
        val base = AppSettings()
        // distinctUntilChanged 靠 equals：无关字段变化必须保持键相等，否则主题树白重组。
        assertEquals(base.themeConfig(), base.copy(danmakuFontSizeSp = 30).themeConfig())
        assertEquals(base.themeConfig(), base.copy(proxyIp = "1.2.3.4").themeConfig())
        assertEquals(base.themeConfig(), base.copy(autoPlayNext = !base.autoPlayNext).themeConfig())
    }

    @Test
    fun `改任一主题量改变去重键`() {
        val base = AppSettings()
        assertNotEquals(base.themeConfig(), base.copy(themeId = "midnight").themeConfig())
        assertNotEquals(base.themeConfig(), base.copy(amoled = !base.amoled).themeConfig())
        assertNotEquals(
            base.themeConfig(),
            base.copy(themeMode = ThemeMode.Dark).themeConfig(),
        )
        assertNotEquals(
            base.themeConfig(),
            base.copy(contrastLevel = ContrastLevel.High).themeConfig(),
        )
    }
}
