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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 网关产物映射回归（纯逻辑，离线可跑）。
 *
 * 产物缺失的平台（Android 同样报 Linux，但包里无资源）由
 * `currentArtifact()` 的资源存在性判定挡掉——那条要真机/真包，
 * 这里只钉 OS/架构 → 文件名的映射。
 */
class EchGateProcessTest {

    @Test
    fun `windows-amd64 选新产物名`() {
        assertEquals(
            EchGateProcess.Artifact("/echgate-windows-amd64.exe", "echgate.exe"),
            EchGateProcess.artifactNameFor("Windows 11", "amd64"),
        )
    }

    @Test
    fun `windows-arm64 无产物`() {
        assertNull(EchGateProcess.artifactNameFor("Windows 11", "aarch64"))
    }

    @Test
    fun `mac-arm64`() {
        assertEquals(
            EchGateProcess.Artifact("/echgate-darwin-arm64", "echgate"),
            EchGateProcess.artifactNameFor("Mac OS X", "aarch64"),
        )
    }

    @Test
    fun `mac-intel`() {
        assertEquals(
            EchGateProcess.Artifact("/echgate-darwin-amd64", "echgate"),
            EchGateProcess.artifactNameFor("Mac OS X", "x86_64"),
        )
    }

    @Test
    fun `linux-amd64`() {
        assertEquals(
            EchGateProcess.Artifact("/echgate-linux-amd64", "echgate"),
            EchGateProcess.artifactNameFor("Linux", "amd64"),
        )
    }

    @Test
    fun `未知平台无产物`() {
        assertNull(EchGateProcess.artifactNameFor("SunOS", "sparc"))
        assertNull(EchGateProcess.artifactNameFor("Linux", "arm"))
    }
}

/**
 * 网关就绪等待回归（冷启动竞态的根治）。
 *
 * 只等"拉起中"：开关没开 / 从未启动时零延迟返回 false，请求立即走兜底；
 * 拉起中则等到就绪或超时。`EchGate.status` 是进程全局，用完即还原，
 * 不污染同 JVM 的其它用例。
 */
class EchGateAwaitTest {

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
        EchGate.publish(EchGateStatus.Idle)
        runBlocking { SettingsRepository.update { it.copy(useEchGate = false) } }
    }

    @Test
    fun `已就绪立即返回true`() {
        install(false)
        EchGate.publish(EchGateStatus.Running(12345))
        try {
            assertTrue(EchGateProcess.awaitReadyIfStarting(500))
        } finally {
            resetGlobals()
        }
    }

    @Test
    fun `从未拉起不等直接返回false`() {
        install(true)
        EchGate.publish(EchGateStatus.Idle)
        try {
            val start = System.currentTimeMillis()
            assertFalse(EchGateProcess.awaitReadyIfStarting(2_000))
            assertTrue(
                System.currentTimeMillis() - start < 1_000,
                "没拉起还等，就是在浪费请求",
            )
        } finally {
            resetGlobals()
        }
    }

    @Test
    fun `开关没开即使starting也不等`() {
        install(false)
        EchGate.publish(EchGateStatus.Starting)
        try {
            assertFalse(EchGateProcess.awaitReadyIfStarting(2_000))
        } finally {
            resetGlobals()
        }
    }

    @Test
    fun `拉起中等待就绪`() {
        install(true)
        EchGate.publish(EchGateStatus.Starting)
        try {
            thread(start = true, isDaemon = true) {
                Thread.sleep(300)
                EchGate.publish(EchGateStatus.Running(12345))
            }
            assertTrue(EchGateProcess.awaitReadyIfStarting(3_000))
        } finally {
            resetGlobals()
        }
    }

    @Test
    fun `拉起中超時返回false`() {
        install(true)
        EchGate.publish(EchGateStatus.Starting)
        try {
            assertFalse(EchGateProcess.awaitReadyIfStarting(400))
        } finally {
            resetGlobals()
        }
    }

    @Test
    fun `等待结束后闸门释放`() {
        install(true)
        EchGate.publish(EchGateStatus.Starting)
        try {
            assertFalse(EchGateProcess.awaitReadyIfStarting(200), "第一次等待超時")
            // 闸门若不释放，第二次调用会被直接挡掉、零延迟返回，于是"网关起得慢一点"
            // 的场景里再也没人等它 —— 首屏那批请求之后进来的请求会永远走兜底。
            thread(start = true, isDaemon = true) {
                Thread.sleep(150)
                EchGate.publish(EchGateStatus.Running(12345))
            }
            assertTrue(EchGateProcess.awaitReadyIfStarting(3_000), "闸门没释放，没人再等网关")
        } finally {
            resetGlobals()
        }
    }
}
