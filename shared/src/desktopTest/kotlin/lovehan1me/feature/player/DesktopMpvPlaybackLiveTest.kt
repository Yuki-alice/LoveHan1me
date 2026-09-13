package lovehan1me.feature.player

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 桌面 mpv 的**真实播放**冒烟（默认 skip，手工开启）。
 *
 * 为什么需要它：本轮定位到"页面能开、视频打不开（mpv_error=-13）"的根因是
 * **mpv 是独立原生网络栈，不继承 OkHttp 的代理**（实测同一流地址：
 * 直连 = `Connection was reset`；经 `127.0.0.1:7897` = **206** 0.3 秒）。
 * 这类结论只能在"真引擎 + 真流 + 真代理"下验证，纯逻辑单测证明不了。
 *
 * 默认 **skip**（未设环境变量时立即返回），因为它要联网、要起 mpv 原生库。
 *
 * 跑法（PowerShell）——注意必须先 `--stop`，否则新环境变量传不进已在运行的守护进程：
 * ```
 * $env:HAN1ME_MPV_LIVE_URL='https://vdownload.hembed.com/xxx-1080p.mp4?secure=...'
 * $env:HAN1ME_MPV_LIVE_PROXY='http://127.0.0.1:7897'
 * ./gradlew --stop
 * ./gradlew :shared:desktopTest --tests '*DesktopMpvPlaybackLiveTest*'
 * ```
 * `HAN1ME_MPV_LIVE_PROXY` 留空即验证"直连"路径（应当失败，用来复现根因）。
 */
class DesktopMpvPlaybackLiveTest {

    @Test
    fun `经代理能打开视频流`() {
        val url = System.getenv("HAN1ME_MPV_LIVE_URL")?.takeIf { it.isNotBlank() }
        if (url == null) {
            println("[skip] 未设 HAN1ME_MPV_LIVE_URL —— 跳过真实播放冒烟（见类 KDoc）")
            return
        }
        val proxy = System.getenv("HAN1ME_MPV_LIVE_PROXY")?.takeIf { it.isNotBlank() }
        println("[live] proxy=$proxy url=${url.take(90)}")

        val state = playAndWait(url, proxy)
        println("[live] phase=${state.phase} size=${state.videoWidth}x${state.videoHeight} err=${state.errorMessage}")
        assertEquals(
            PlaybackPhase.Ready,
            state.phase,
            "视频流未进入 Ready（proxy=$proxy）：${state.errorMessage}",
        )
    }

    /** 载入并等到 Ready/Error/超时，返回那一刻的状态。 */
    private fun playAndWait(url: String, proxy: String?): PlaybackEngineState = runBlocking {
        val engine = DesktopMpvPlaybackEngine(mediaProxyUrl = { proxy })
        try {
            engine.load(PlaybackRequest(uri = url, playWhenReady = true))
            withTimeoutOrNull(30_000) {
                engine.state.first { it.phase == PlaybackPhase.Ready || it.phase == PlaybackPhase.Error }
            } ?: engine.state.value
        } finally {
            engine.release()
        }
    }
}
