package lovehan1me.data.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 网关生命周期状态的回归。
 *
 * 这一组里**主动停止 ≠ 失败**那条是守卫用例：把
 * [EchGateStatus.onProcessOutputEnded] 改成无条件落 [EchGateStatus.Exited]，
 * 它必须变红 —— 那正是"用户关掉开关，设置页却报网关失败"的复现路径。
 */
class EchGateStatusTest {

    @Test
    fun `主动关开关后读到的是已停止而不是失败`() {
        // 复刻真实顺序：stop() 先把状态落成 Stopped，监视器随后才收到 stdout EOF。
        var status: EchGateStatus = EchGateStatus.Running(1234)
        status = EchGateStatus.Stopped
        status = status.onProcessOutputEnded(ownedByCurrentMonitor = false)

        assertEquals(EchGateStatus.Stopped, status)
        assertNull(status.lastError, "关掉开关不该报「网关失败：网关进程已退出」")
    }

    @Test
    fun `已不归本监视器管时输出结束不改状态`() {
        val running = EchGateStatus.Running(1234)
        assertEquals(running, running.onProcessOutputEnded(ownedByCurrentMonitor = false))
    }

    @Test
    fun `仍归本监视器管时主动停止也保持已停止`() {
        assertEquals(
            EchGateStatus.Stopped,
            EchGateStatus.Stopped.onProcessOutputEnded(),
        )
    }

    @Test
    fun `运行中的进程输出结束判为意外退出`() {
        assertEquals(EchGateStatus.Exited, EchGateStatus.Running(1234).onProcessOutputEnded())
    }

    @Test
    fun `拉起中进程输出结束同样判为意外退出`() {
        assertEquals(EchGateStatus.Exited, EchGateStatus.Starting.onProcessOutputEnded())
    }

    @Test
    fun `只有 Running 给得出端口`() {
        assertEquals(1234, EchGateStatus.Running(1234).port)
        assertEquals(-1, EchGateStatus.Idle.port)
        assertEquals(-1, EchGateStatus.Starting.port)
        assertEquals(-1, EchGateStatus.Stopped.port)
        assertEquals(-1, EchGateStatus.Exited.port)
        assertEquals(-1, EchGateStatus.Failed("解包网关失败").port)
    }

    @Test
    fun `starting 只认 Starting`() {
        assertTrue(EchGateStatus.Starting.starting)
        assertFalse(EchGateStatus.Running(1234).starting)
        assertFalse(EchGateStatus.Idle.starting)
        assertFalse(EchGateStatus.Stopped.starting)
    }

    @Test
    fun `失败与意外退出给原因 主动停止与运行中不给`() {
        assertEquals("解包网关失败", EchGateStatus.Failed("解包网关失败").lastError)
        assertNotNull(EchGateStatus.Exited.lastError, "意外退出要能在设置页被看见")
        assertNull(EchGateStatus.Stopped.lastError)
        assertNull(EchGateStatus.Running(1234).lastError)
        assertNull(EchGateStatus.Idle.lastError)
    }
}
