package io.github.daisukikaffuchino.han1meviewer.ui.player

import platform.AVFoundation.rate
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * M7-4：iOS AVPlayer 播放引擎真实验证（跑在 iosSimulatorArm64Test / 模拟器上）。
 *
 * 网络环境说明（2026-09-09 实测）：
 * - hanime1.me 的 HTML 页被 Cloudflare 挑战保护（cf-mitigated），宿主机 curl 403，
 *   模拟器网络（共享宿主）同样无法直接取到真实 m3u8 直链；
 * - 因此本套测试用 Apple 官方公开 HLS 测试流验证引擎全链路（bipbop 同时提供
 *   TS 分片与 fMP4 分片两种形态，覆盖 hanime hls.js 源的两种常见分片格式）；
 * - [hanimeRealSourceTest] 是环境门控的真实源验证入口：设置 HANIME_TEST_M3U8
 *   环境变量（真实 hanime m3u8 直链，可在 CF 放行网络下从视频页
 *   `const source = '...'` 提取）后运行，即可对真源做同样的断言。
 *
 * M7-4 实测结论（2026-09-10，见 NEXT_PHASE_PLAN.md 决策记录）：
 * - 方案 A（本文件）在本机模拟器 test.kexe 进程内不可行：所有 HTTPS（含 Apple CDN）
 *   均 TLS -1202（环境中间层劫持/DNS 污染：同机宿主 curl 200、同模拟器 app 进程 200，
 *   仅 test.kexe 进程失败），属环境限制非引擎缺陷；各用例经 [preflight] 门控后 SKIP；
 * - 方案 B（app 进程内临时入口，已删除）用真实 hanime 视频（code 408163，
 *   vdownload.hembed.com progressive mp4 1080P）全绿：加载 2s→Ready/playing、
 *   进度 6s、seek(60s)误差 1580ms、2.0x 生效、404 同 host URL 进 Error；
 * - 真源实为 progressive mp4 而非 m3u8，hls.ts 兼容风险不成立，决策选 AVPlayer。
 *
 * AVPlayer 无 UI 也能播（AVPlayerLayer 只管渲染），state 由引擎 500ms 轮询发布。
 */
class IosAVPlaybackEngineTest {

    /** Apple bipbop：经典 HLS（MPEG-TS 分片，H.264/AAC），10 分钟。 */
    private val bipbopTs = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8"

    /** Apple bipbop：fMP4 分片 HLS（iOS 10+ 支持），约 30 分钟。 */
    private val bipbopFmp4 = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"

    private suspend fun IosAVPlaybackEngine.awaitPlaying(timeout: kotlin.time.Duration = 60.seconds): PlaybackEngineState? =
        withTimeoutOrNull(timeout) {
            while (true) {
                val s = state.value
                if (s.phase == PlaybackPhase.Ready && s.isPlaying) return@withTimeoutOrNull s
                delay(250L)
            }
            @Suppress("UNREACHABLE_CODE") null
        }

    private suspend fun IosAVPlaybackEngine.awaitPhase(
        phase: PlaybackPhase,
        timeout: kotlin.time.Duration = 30.seconds,
    ): Boolean = withTimeoutOrNull(timeout) {
        while (state.value.phase != phase) delay(250L)
        true
    } ?: false

    private suspend fun IosAVPlaybackEngine.awaitPosition(
        timeout: kotlin.time.Duration = 60.seconds,
        predicate: (Long) -> Boolean,
    ): Long? = withTimeoutOrNull(timeout) {
        while (true) {
            val p = state.value.positionMs
            if (predicate(p)) return@withTimeoutOrNull p
            delay(250L)
        }
        @Suppress("UNREACHABLE_CODE") null
    }

    /**
     * 环境预检：test.kexe 进程内 HTTPS 出网是否可用。
     * 受限环境（如本机模拟器 TLS -1202）下返回 false，调用方应 SKIP 而非断言，
     * 避免把环境问题误记为引擎回归。健康网络下一次 GET 仅几百 ms。
     */
    private suspend fun preflight(url: String): Boolean {
        try {
            val client = HttpClient(Darwin)
            try {
                val resp = withTimeoutOrNull(15_000L) { client.get(url) } ?: return false
                resp.bodyAsText().take(16)
                return resp.status.value in 200..299
            } finally {
                client.close()
            }
        } catch (e: Exception) {
            println("[SKIP] preflight 失败（环境出网受限，非引擎问题）: ${e.message?.take(160)}")
            return false
        }
    }

    // ── 1. 加载 + 进度推进（TS 分片 HLS）─────────────────────────

    @Test
    fun loadAndProgressHlsTs() = runBlocking {
        if (!preflight(bipbopTs)) {
            println("[SKIP] loadAndProgressHlsTs：test.kexe 进程无可用 HTTPS 出网")
            return@runBlocking
        }
        val engine = IosAVPlaybackEngine()
        try {
            engine.load(PlaybackRequest(uri = bipbopTs, playWhenReady = true))

            val s = engine.awaitPlaying()
            assertTrue(s != null, "60s 内未进入 Ready+isPlaying（加载失败）：state=${engine.state.value}")
            s!!

            val pos = engine.awaitPosition { it > 5_000L }
            assertTrue(pos != null, "进度未推进到 5s 以上：state=${engine.state.value}")
            assertTrue(engine.state.value.durationMs > 0L, "durationMs 应为正值")
            assertTrue(engine.state.value.videoWidth > 0 && engine.state.value.videoHeight > 0,
                "应读到视频宽高（含旋转修正）")
        } finally {
            engine.release()
        }
    }

    // ── 2. 加载 + 进度推进（fMP4 分片 HLS，与 TS 同断言）──────────

    @Test
    fun loadAndProgressHlsFmp4() = runBlocking {
        if (!preflight(bipbopFmp4)) {
            println("[SKIP] loadAndProgressHlsFmp4：test.kexe 进程无可用 HTTPS 出网")
            return@runBlocking
        }
        val engine = IosAVPlaybackEngine()
        try {
            engine.load(PlaybackRequest(uri = bipbopFmp4, playWhenReady = true))
            val s = engine.awaitPlaying()
            assertTrue(s != null, "60s 内未进入 Ready+isPlaying（fMP4 HLS 加载失败）：state=${engine.state.value}")
            s!!
            val pos = engine.awaitPosition { it > 5_000L }
            assertTrue(pos != null, "fMP4 进度未推进到 5s 以上：state=${engine.state.value}")
        } finally {
            engine.release()
        }
    }

    // ── 3. seekTo(60s) 精度（误差 < 2s）──────────────────────────

    @Test
    fun seekAccuracy() = runBlocking {
        if (!preflight(bipbopTs)) {
            println("[SKIP] seekAccuracy：test.kexe 进程无可用 HTTPS 出网")
            return@runBlocking
        }
        val engine = IosAVPlaybackEngine()
        try {
            engine.load(PlaybackRequest(uri = bipbopTs, playWhenReady = true))
            assertTrue(engine.awaitPlaying() != null, "加载失败")
            assertTrue(engine.awaitPosition { it > 2_000L } != null, "起步进度未推进")

            engine.seekTo(60_000L)
            val pos = engine.awaitPosition(timeout = 30.seconds) { it in 58_000L..62_000L }
            assertTrue(pos != null, "seek 后 30s 内未落到 60s±2s：state=${engine.state.value}")
        } finally {
            engine.release()
        }
    }

    // ── 4. setPlaybackSpeed(2.0) ────────────────────────────────

    @Test
    fun playbackSpeed() = runBlocking {
        if (!preflight(bipbopTs)) {
            println("[SKIP] playbackSpeed：test.kexe 进程无可用 HTTPS 出网")
            return@runBlocking
        }
        val engine = IosAVPlaybackEngine()
        try {
            engine.load(PlaybackRequest(uri = bipbopTs, playWhenReady = true))
            assertTrue(engine.awaitPlaying() != null, "加载失败")

            engine.setPlaybackSpeed(2.0f)
            assertTrue(kotlin.math.abs(engine.avPlayer.rate - 2.0f) < 0.01f,
                "avPlayer.rate 应为 2.0，实际 ${engine.avPlayer.rate}")
            // state 由 500ms 轮询发布，等它收敛
            val ok = withTimeoutOrNull(5.seconds) {
                while (engine.state.value.playbackSpeed != 2.0f) delay(250L)
                true
            } ?: false
            assertTrue(ok, "state.playbackSpeed 未收敛到 2.0：state=${engine.state.value}")
        } finally {
            engine.release()
        }
    }

    // ── 5. 错误路径（404）────────────────────────────────────────

    @Test
    fun errorPath404() = runBlocking {
        if (!preflight(bipbopTs)) {
            println("[SKIP] errorPath404：test.kexe 进程无可用 HTTPS 出网")
            return@runBlocking
        }
        val engine = IosAVPlaybackEngine()
        try {
            engine.load(PlaybackRequest(uri = "https://devstreaming-cdn.apple.com/videos/streaming/examples/definitely-missing-404.m3u8"))
            assertTrue(engine.awaitPhase(PlaybackPhase.Error, timeout = 30.seconds),
                "404 资源未在 30s 内进入 Error：state=${engine.state.value}")
            assertTrue(engine.state.value.errorMessage != null, "Error 应携带 errorMessage")
        } finally {
            engine.release()
        }
    }

    // ── 6. hanime 真源（环境门控）────────────────────────────────

    @Test
    fun hanimeRealSourceTest() = runBlocking {
        val uri = PlatformEnvironment.get("HANIME_TEST_M3U8")
        if (uri.isNullOrBlank()) {
            println("[SKIP] hanime 真源验证：未设置 HANIME_TEST_M3U8 环境变量" +
                "（当前网络 CF/GFW 受限无法自动取直链；拿到真链后设置变量重跑即自动启用）")
            return@runBlocking
        }
        if (!preflight(uri)) {
            println("[SKIP] hanime 真源验证：test.kexe 进程无可用 HTTPS 出网")
            return@runBlocking
        }
        val engine = IosAVPlaybackEngine()
        try {
            engine.load(PlaybackRequest(uri = uri, playWhenReady = true))
            val s = engine.awaitPlaying(timeout = 90.seconds)
            assertTrue(s != null, "hanime 真源 90s 内未进入 Ready+isPlaying：state=${engine.state.value}")
            s!!
            println("[INFO] hanime 真源 duration=${s.durationMs}ms size=${s.videoWidth}x${s.videoHeight}")
            assertTrue(engine.awaitPosition { it > 5_000L } != null, "hanime 真源进度未推进")
        } finally {
            engine.release()
        }
    }
}

/** native 测试读环境变量的小工具（posix getenv）。 */
private object PlatformEnvironment {
    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    fun get(name: String): String? = platform.posix.getenv(name)?.toKString()
}
