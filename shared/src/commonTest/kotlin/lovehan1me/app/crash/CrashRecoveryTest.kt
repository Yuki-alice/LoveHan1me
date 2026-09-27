package lovehan1me.app.crash

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 桌面 resize 渲染竞态判别。
 *
 * 覆盖用户实测的闪退堆栈顶（`RootNodeOwner is already disposed`，
 * EDT 经 SkiaLayer.update → ComposeSceneMediator.onRender 到达），
 * 以及关弹窗场景的同类 teardown 竞态（`ComposeScene is closed`）。
 * 误判的代价是真崩溃被吞掉，因此反例（其它消息/类型/空消息）必须判 false。
 */
class CrashRecoveryTest {

    @Test
    fun `RootNodeOwner 已销毁判 benign`() {
        assertTrue(
            isBenignDesktopRenderRace(
                IllegalArgumentException("RootNodeOwner is already disposed"),
            ),
        )
    }

    @Test
    fun `包了多层 cause 仍能判 benign`() {
        val root = IllegalArgumentException("RootNodeOwner is already disposed")
        val wrapped = RuntimeException("render failed", IllegalStateException("mid", root))
        assertTrue(isBenignDesktopRenderRace(wrapped))
    }

    @Test
    fun `ComposeScene 已关闭判 benign`() {
        assertTrue(
            isBenignDesktopRenderRace(
                IllegalStateException("ComposeScene is closed"),
            ),
        )
    }

    @Test
    fun `同类型不同消息不判 benign`() {
        assertFalse(
            isBenignDesktopRenderRace(
                IllegalArgumentException("Failed requirement."),
            ),
        )
        assertFalse(
            isBenignDesktopRenderRace(
                IllegalStateException("ComposeScene is disposed"),
            ),
        )
    }

    @Test
    fun `空消息与其他类型不判 benign`() {
        assertFalse(isBenignDesktopRenderRace(IllegalArgumentException()))
        assertFalse(isBenignDesktopRenderRace(RuntimeException("boom")))
    }
}
