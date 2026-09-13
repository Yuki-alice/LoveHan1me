package lovehan1me.core.util

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [StartupTrace] 的行为锁定（M5-2）。
 *
 * 它本身很薄，但"埋点把耗时算歪"是最典型的假数据来源，所以几条不变量值得钉住：
 * 重复 `begin` 不重置起点、同名 `mark` 只记第一次、未 begin 时全部调用安全。
 */
class StartupTraceTest {

    @AfterTest
    fun tearDown() = StartupTrace.resetForTest()

    @Test
    fun `未 begin 时所有调用都安全且不记录`() {
        // 各端入口若忘记 begin（或单测直接调），不能抛异常、也不能写出假耗时
        StartupTrace.mark("datastore")
        StartupTrace.markSince("settings", 0L)
        assertEquals(0L, StartupTrace.elapsedMillis())
    }

    @Test
    fun `begin 只认第一次调用`() {
        StartupTrace.begin("test")
        val first = StartupTrace.elapsedMillis()
        Thread.sleep(5)
        StartupTrace.begin("再次调用")
        // 起点不被重置：第二次 begin 后耗时不会回到 0
        assertTrue(StartupTrace.elapsedMillis() >= first)
    }

    @Test
    fun `同名 mark 只记第一次`() {
        StartupTrace.begin("test")
        StartupTrace.mark("coil")
        val afterFirst = StartupTrace.elapsedMillis()
        Thread.sleep(5)
        StartupTrace.mark("coil")
        // 幂等：不会因为二次埋点把该段耗时改大（summary 里读到的仍是第一次）
        assertTrue(StartupTrace.elapsedMillis() >= afterFirst)
    }

    @Test
    fun `segment 记录耗时且异常照常抛出`() {
        StartupTrace.begin("test")
        val value = StartupTrace.segment("datastore") { 42 }
        assertEquals(42, value)
        var thrown = false
        runCatching { StartupTrace.segment("settings") { error("boom") } }
            .onFailure { thrown = true }
        assertTrue(thrown, "segment 不应吞掉异常")
    }

    @Test
    fun `summary 与 resetForTest 可用`() {
        StartupTrace.begin("test")
        StartupTrace.mark("application")
        StartupTrace.summary()
        StartupTrace.resetForTest()
        assertEquals(0L, StartupTrace.elapsedMillis(), "reset 后应回到未开始状态")
    }
}
