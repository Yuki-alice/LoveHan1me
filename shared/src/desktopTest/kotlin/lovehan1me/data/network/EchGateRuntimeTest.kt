package lovehan1me.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.data.SettingsRepository
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 桌面网关产物映射回归（纯逻辑，离线可跑）。
 *
 * 产物缺失的平台（未覆盖的架构）由 `currentArtifact()` 的资源存在性判定挡掉
 * ——那条要真机/真包，这里只钉 OS/架构 → 文件名的映射。
 */
class EchGateArtifactTest {

    @Test
    fun `windows-amd64 选新产物名`() {
        assertEquals(
            DesktopEchGateStarter.Artifact("/echgate-windows-amd64.exe", "echgate.exe"),
            DesktopEchGateStarter.artifactNameFor("Windows 11", "amd64"),
        )
    }

    @Test
    fun `windows-arm64 无产物`() {
        assertNull(DesktopEchGateStarter.artifactNameFor("Windows 11", "aarch64"))
    }

    @Test
    fun `mac-arm64`() {
        assertEquals(
            DesktopEchGateStarter.Artifact("/echgate-darwin-arm64", "echgate"),
            DesktopEchGateStarter.artifactNameFor("Mac OS X", "aarch64"),
        )
    }

    @Test
    fun `mac-intel`() {
        assertEquals(
            DesktopEchGateStarter.Artifact("/echgate-darwin-amd64", "echgate"),
            DesktopEchGateStarter.artifactNameFor("Mac OS X", "x86_64"),
        )
    }

    @Test
    fun `linux-amd64`() {
        assertEquals(
            DesktopEchGateStarter.Artifact("/echgate-linux-amd64", "echgate"),
            DesktopEchGateStarter.artifactNameFor("Linux", "amd64"),
        )
    }

    @Test
    fun `未知平台无产物`() {
        assertNull(DesktopEchGateStarter.artifactNameFor("SunOS", "sparc"))
        assertNull(DesktopEchGateStarter.artifactNameFor("Linux", "arm"))
    }
}

/** 门面回归用的假运行时：起服结局与阻塞时长都由用例指定。 */
private class FakeStarter(
    private val onStart: () -> Boolean = { true },
) : EchGateStarter {
    var stopCount = 0
        private set

    override fun start(): Boolean = onStart()

    override fun stop() {
        stopCount++
    }
}

/**
 * 运行时门面（[EchGateRuntime]）回归。骨架与"哪个平台的运行时"无关，
 * 故这里全部用 [FakeStarter] 驱动 —— 真产物缺失不该让骨架的行为无法断言。
 *
 * `EchGate.status` 与装配的 starter 都是**进程全局状态**，每条用例用完即还原，
 * 否则同 JVM 的其它用例会读到上一条残留的 Running/Failed。
 */
class EchGateRuntimeTest {

    private class GateTestStore(initial: AppSettings) : SettingsStore {
        private val state = MutableStateFlow(initial)
        override val settings: StateFlow<AppSettings> = state
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            state.value = transform(state.value)
        }
    }

    private fun install(useEchGate: Boolean) {
        runCatching {
            SettingsRepository.install(GateTestStore(AppSettings(useEchGate = useEchGate)))
        }
        runBlocking { SettingsRepository.update { it.copy(useEchGate = useEchGate) } }
    }

    private fun resetGlobals() {
        EchGateRuntime.uninstall()
        EchGate.publish(EchGateStatus.Idle)
        runBlocking { SettingsRepository.update { it.copy(useEchGate = false) } }
    }

    /** 轮询等一个状态，避免用例依赖固定 sleep 时长。 */
    private fun awaitStatus(timeoutMs: Long = 3_000, predicate: (EchGateStatus) -> Boolean): EchGateStatus {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val s = EchGate.status
            if (predicate(s)) return s
            Thread.sleep(10)
        }
        return EchGate.status
    }

    // ---------- 未装配运行时 ----------

    @Test
    fun `未装配运行时 start 落失败而非卡住`() {
        install(useEchGate = true)
        try {
            assertFalse(EchGateRuntime.start())
            val failed = assertIs<EchGateStatus.Failed>(EchGate.status)
            assertEquals("当前平台无网关产物", failed.reason)
        } finally {
            resetGlobals()
        }
    }

    // ---------- 起服结局 ----------

    @Test
    fun `起服成功落 Running 并回填端口`() {
        install(useEchGate = true)
        EchGateRuntime.install(FakeStarter {
            EchGate.publish(EchGateStatus.Running(23456))
            true
        })
        try {
            assertTrue(EchGateRuntime.start())
            awaitStatus { it is EchGateStatus.Running }
            assertEquals(23456, EchGate.port)
        } finally {
            resetGlobals()
        }
    }

    @Test
    fun `一个不留终态的起服器不会把状态卡在 Starting`() {
        install(useEchGate = true)
        // 模拟"起服失败但忘了 publish 原因"的实现：骨架必须兜一次 Failed，
        // 否则拦截器会永远看到 Starting（既不等待就绪、也不进自愈分支）。
        EchGateRuntime.install(FakeStarter { false })
        try {
            assertTrue(EchGateRuntime.start())
            assertIs<EchGateStatus.Failed>(awaitStatus { it !is EchGateStatus.Starting })
            assertEquals(-1, EchGate.port)
        } finally {
            resetGlobals()
        }
    }

    @Test
    fun `已在运行时不重复起服`() {
        install(useEchGate = true)
        var startCount = 0
        EchGateRuntime.install(FakeStarter {
            startCount++
            EchGate.publish(EchGateStatus.Running(12345))
            true
        })
        try {
            EchGate.publish(EchGateStatus.Running(12345))
            assertTrue(EchGateRuntime.start())
            assertTrue(EchGateRuntime.start())
            assertEquals(0, startCount, "已就绪还去起服")
        } finally {
            resetGlobals()
        }
    }

    // ---------- 起服尾段的停止竞态 ----------

    @Test
    fun `起服尾段在仍处 Starting 时接管`() {
        install(useEchGate = true)
        EchGateRuntime.install(FakeStarter {
            EchGateRuntime.publishRunningIfStillStarting(23456)
            true
        })
        try {
            EchGateRuntime.start()
            awaitStatus { it is EchGateStatus.Running }
            assertEquals(23456, EchGate.port)
        } finally {
            resetGlobals()
        }
    }

    @Test
    fun `起服期间被停止时就绪发布必须失败`() {
        // 形态取自"起服器返回 true 并且自己补 publish"（进程内起服就是这个形状）：
        // 起服器睡够一段时间才回来，模拟首次 DoH 的长阻塞；期间用户关掉开关。
        install(useEchGate = true)
        var accepted: Boolean? = null
        EchGateRuntime.install(FakeStarter {
            Thread.sleep(200)
            accepted = EchGateRuntime.publishRunningIfStillStarting(23456)
            true
        })
        try {
            EchGateRuntime.start()
            Thread.sleep(50)
            EchGateRuntime.stop()
            Thread.sleep(400)
            assertEquals(false, accepted, "停止期间起的服被判成了就绪 —— 会留下关不掉的网关")
            assertIs<EchGateStatus.Stopped>(EchGate.status)
            assertEquals(-1, EchGate.port)
        } finally {
            resetGlobals()
        }
    }

    // ---------- 停止语义 ----------

    @Test
    fun `停止时先落 Stopped 再回收资源`() {
        // 顺序本身是契约：运行时回收时若还没落 Stopped，它的收尾逻辑会把这次停止
        // 当成"意外死亡"（桌面监视器的 owned 判定、进程内实现的服端回收都看这个）。
        install(useEchGate = true)
        var statusWhenRecycling: EchGateStatus? = null
        EchGateRuntime.install(object : EchGateStarter {
            override fun start(): Boolean {
                EchGate.publish(EchGateStatus.Running(12345))
                return true
            }

            override fun stop() {
                statusWhenRecycling = EchGate.status
            }
        })
        try {
            EchGateRuntime.start()
            awaitStatus { it is EchGateStatus.Running }
            EchGateRuntime.stop()
            assertIs<EchGateStatus.Stopped>(statusWhenRecycling)
        } finally {
            resetGlobals()
        }
    }

    @Test
    fun `主动停止落 Stopped 且不记成失败`() {
        install(useEchGate = true)
        val fake = FakeStarter {
            Thread.sleep(150)
            EchGate.publish(EchGateStatus.Running(12345))
            true
        }
        EchGateRuntime.install(fake)
        try {
            EchGateRuntime.start()
            awaitStatus { it is EchGateStatus.Running }
            EchGateRuntime.stop()
            assertIs<EchGateStatus.Stopped>(EchGate.status)
            assertNull(EchGate.status.lastError, "主动停不该留下失败原因")
            assertEquals(1, fake.stopCount)
        } finally {
            resetGlobals()
        }
    }

    @Test
    fun `起服途中被停止不会把 Stopped 覆盖成失败`() {
        install(useEchGate = true)
        // 起服器还在阻塞（真实场景：桌面在探测上游 IP、Android 在等首次 DoH），
        // 用户这时关掉开关。起服线程随后返回 false，**不得**再落 Failed。
        EchGateRuntime.install(FakeStarter {
            Thread.sleep(250)
            false
        })
        try {
            EchGateRuntime.start()
            Thread.sleep(50)
            EchGateRuntime.stop()
            Thread.sleep(400)
            assertIs<EchGateStatus.Stopped>(EchGate.status)
            assertNull(EchGate.status.lastError)
        } finally {
            resetGlobals()
        }
    }

    // ---------- 就绪等待 ----------

    @Test
    fun `已就绪立即返回true`() {
        install(useEchGate = false)
        EchGate.publish(EchGateStatus.Running(12345))
        try {
            assertTrue(EchGateRuntime.awaitReadyIfStarting(500))
        } finally {
            resetGlobals()
        }
    }

    @Test
    fun `开关没开即使starting也不等`() {
        install(useEchGate = false)
        EchGate.publish(EchGateStatus.Starting)
        try {
            assertFalse(EchGateRuntime.awaitReadyIfStarting(2_000))
        } finally {
            resetGlobals()
        }
    }

    @Test
    fun `从未拉起不等直接返回false`() {
        install(useEchGate = true)
        EchGate.publish(EchGateStatus.Idle)
        try {
            val start = System.currentTimeMillis()
            assertFalse(EchGateRuntime.awaitReadyIfStarting(2_000))
            assertTrue(
                System.currentTimeMillis() - start < 1_000,
                "没拉起还等，就是在浪费请求",
            )
        } finally {
            resetGlobals()
        }
    }

    @Test
    fun `拉起中等待就绪`() {
        install(useEchGate = true)
        EchGate.publish(EchGateStatus.Starting)
        try {
            thread(start = true, isDaemon = true) {
                Thread.sleep(300)
                EchGate.publish(EchGateStatus.Running(12345))
            }
            assertTrue(EchGateRuntime.awaitReadyIfStarting(3_000))
        } finally {
            resetGlobals()
        }
    }

    @Test
    fun `拉起中超時返回false`() {
        install(useEchGate = true)
        EchGate.publish(EchGateStatus.Starting)
        try {
            assertFalse(EchGateRuntime.awaitReadyIfStarting(400))
        } finally {
            resetGlobals()
        }
    }

    @Test
    fun `等待结束后闸门释放`() {
        install(useEchGate = true)
        EchGate.publish(EchGateStatus.Starting)
        try {
            assertFalse(EchGateRuntime.awaitReadyIfStarting(200), "第一次等待超時")
            // 闸门若不释放，第二次调用会被直接挡掉、零延迟返回，于是"网关起得慢一点"
            // 的场景里再也没人等它 —— 首屏那批请求之后进来的请求会永远走兜底。
            thread(start = true, isDaemon = true) {
                Thread.sleep(150)
                EchGate.publish(EchGateStatus.Running(12345))
            }
            assertTrue(EchGateRuntime.awaitReadyIfStarting(3_000), "闸门没释放，没人再等网关")
        } finally {
            resetGlobals()
        }
    }
}
