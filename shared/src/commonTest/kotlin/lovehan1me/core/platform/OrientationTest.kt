package lovehan1me.core.platform

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 宽高比判定回归（纯函数，零 flake）。
 *
 * 钉住"只有明确更宽才算横屏"：正方形与非法尺寸一律竖屏，
 * 与 Android `Configuration` 语义对齐。
 */
class OrientationTest {

    @Test
    fun `宽大于高才算横屏`() {
        assertTrue(isLandscapeSize(1920, 1080))
        assertTrue(isLandscapeSize(1180, 820))
    }

    @Test
    fun `高大于等于宽算竖屏`() {
        assertFalse(isLandscapeSize(1080, 1920))
        assertFalse(isLandscapeSize(820, 820))
    }

    @Test
    fun `非法尺寸不撒谎`() {
        assertFalse(isLandscapeSize(0, 0))
        assertFalse(isLandscapeSize(0, 1080))
        assertFalse(isLandscapeSize(1920, 0))
        assertFalse(isLandscapeSize(-1, 100))
    }
}
