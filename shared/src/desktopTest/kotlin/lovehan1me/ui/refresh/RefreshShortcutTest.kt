package lovehan1me.ui.refresh

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 刷新快捷键的判定。
 *
 * 跑法：`:shared:desktopTest --tests "lovehan1me.ui.refresh.RefreshShortcutTest"`
 *
 * 两个方向都要钉住：该响应的组合不能漏（漏了用户按 F5 毫无反应），
 * 不该响应的不能误收（否则普通打字会被当成刷新）。
 */
class RefreshShortcutTest {

    @Test
    fun `F5 与带修饰键的 R 都算刷新`() {
        assertTrue(isPageRefreshShortcut(Key.F5, isCtrlPressed = false, isMetaPressed = false))
        assertTrue(isPageRefreshShortcut(Key.F5, isCtrlPressed = true, isMetaPressed = false))
        assertTrue(isPageRefreshShortcut(Key.R, isCtrlPressed = true, isMetaPressed = false))
        assertTrue(isPageRefreshShortcut(Key.R, isCtrlPressed = false, isMetaPressed = true))
    }

    @Test
    fun `光按 R 或别的字母键都不算刷新`() {
        assertFalse(isPageRefreshShortcut(Key.R, isCtrlPressed = false, isMetaPressed = false))
        assertFalse(isPageRefreshShortcut(Key.E, isCtrlPressed = true, isMetaPressed = false))
        assertFalse(
            isPageRefreshShortcut(Key.F4, isCtrlPressed = false, isMetaPressed = false),
        )
    }
}
