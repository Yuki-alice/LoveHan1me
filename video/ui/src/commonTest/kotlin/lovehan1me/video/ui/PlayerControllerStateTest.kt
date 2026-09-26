package lovehan1me.video.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 控件可见性仲裁（D11）：单击、常亮令牌、倒计时各自只改自己那份输入，可见性从它们派生。
 *
 * 三个写者互相覆盖是原症状 —— 弹窗开着被倒计时藏掉、点一下又冒出来。这里把每条
 * 输入与派生结果的关系钉死，[PlayerControllerState.canStartAutoHideTimer] 的条件也一并钉死：
 * 它一旦在"已隐藏"时返回 true，就会隐藏→重计时→再隐藏地空转。
 */
class PlayerControllerStateTest {

    @Test
    fun `D11 初始可见且倒计时可起`() {
        val state = PlayerControllerState()
        assertTrue(state.isControlsVisible)
        assertTrue(state.canStartAutoHideTimer(transientActive = false))
    }

    @Test
    fun `D11 单击切换可见性_倒计时到点后单击能叫回来`() {
        val state = PlayerControllerState()
        state.toggleByTap()
        assertFalse(state.isControlsVisible, "单击没把控件藏起来")

        state.toggleByTap()
        assertTrue(state.isControlsVisible, "再点一下没把控件叫回来")

        state.onAutoHideElapsed()
        assertFalse(state.isControlsVisible, "倒计时到点没藏控件")

        state.toggleByTap()
        assertTrue(state.isControlsVisible, "倒计时藏起来后单击应能叫回来")
    }

    @Test
    fun `D11 常亮请求在场时不起倒计时_撤销后重新可起`() {
        val state = PlayerControllerState()
        state.requestAlwaysOn(MENU)
        assertTrue(state.hasAlwaysOnRequest)
        assertFalse(
            state.canStartAutoHideTimer(transientActive = false),
            "常亮请求在场还允许起倒计时，弹窗会被半路藏掉",
        )

        state.cancelAlwaysOn(MENU)
        assertFalse(state.hasAlwaysOnRequest)
        assertTrue(
            state.canStartAutoHideTimer(transientActive = false),
            "撤销请求后应从头重新计时，而不是立刻隐藏",
        )
    }

    @Test
    fun `D11 已隐藏时不得再允许起倒计时`() {
        val state = PlayerControllerState()
        state.onAutoHideElapsed()
        assertFalse(state.isControlsVisible)
        assertFalse(
            state.canStartAutoHideTimer(transientActive = false),
            "已隐藏还允许重起倒计时：隐藏/重计会互相触发成循环",
        )
        assertFalse(state.alwaysOnActive.value)
    }

    @Test
    fun `D11 手势进行中不起倒计时_结束后恢复`() {
        val state = PlayerControllerState()
        assertFalse(
            state.canStartAutoHideTimer(transientActive = true),
            "手势还没结束就开始倒计时，手势一停控件就消失",
        )
        assertTrue(state.canStartAutoHideTimer(transientActive = false))
    }

    @Test
    fun `D11 多个常亮请求各自独立撤销`() {
        val state = PlayerControllerState()
        state.requestAlwaysOn(MENU)
        state.requestAlwaysOn(SLIDER)
        state.cancelAlwaysOn(MENU)
        assertTrue(state.hasAlwaysOnRequest, "撤销一个请求把另一个也带走了")
        assertFalse(state.canStartAutoHideTimer(transientActive = false))

        state.cancelAlwaysOn(SLIDER)
        assertFalse(state.hasAlwaysOnRequest)
    }

    private companion object {
        val MENU = Any()
        val SLIDER = Any()
    }
}