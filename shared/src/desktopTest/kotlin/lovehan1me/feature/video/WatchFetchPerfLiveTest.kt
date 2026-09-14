package lovehan1me.feature.video

import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.core.domain.state.VideoLoadingState
import lovehan1me.data.NetworkRepo
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.HanimeNetwork
import java.io.File
import java.net.ProxySelector
import java.net.URI
import kotlin.test.Test

/**
 * 视频详情页首开 2-3 分钟卡顿的**复现探针**（live，默认跑）。
 *
 * 复刻用户真实路径（见 2026-09-15 调研 docs/视频播放优化.md A 节）：
 * 冷 OkHttp 缓存 → GET 首页（App 启动路径，替全应用交连接/CF 学费）
 * → GET watch?v=xxx（点第一个视频卡片）→ 再取一次（对照"二次秒开"）。
 *
 * 关键还原点：
 * - **冷缓存**：先把 user.home 指到临时目录，ServiceCreator 的 http_cache 随之落冷目录，
 *   不碰 ~/.lovehan1me 真实缓存（测试零副作用）。
 * - **用户实际配置**：内存 SettingsStore 默认值 = proxy_type=System / useDoH=false /
 *   无 cf_clearance —— 与解码出的真实设置一致（proxy_type=1、use_doh=0、cf_cookie 空）。
 * - 计时用现成 PlayerTrace 分段（video-request/parse-start/end 埋在 videoIOFlow 里），
 *   控制台直接看每个 mark 的 +ms 偏移。
 *
 * 判读：
 * - request-start→request-end 拉满分钟级 → 网络层（代理/CF/超时重试）问题；
 * - parse-start→parse-end 分钟级 → ksoup 解析问题；
 * - 都很快但 UI 仍卡 → 主线程被别的东西堵（引擎构造/组合），转向 Compose 层排查。
 */
class WatchFetchPerfLiveTest {

    private class InMemorySettingsStore : SettingsStore {
        private val state = MutableStateFlow(AppSettings())
        override val settings: StateFlow<AppSettings> = state
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            state.value = transform(state.value)
        }
    }

    @Test
    fun `冷缓存下首页与watch页全链路计时`() = runBlocking {
        // 1. 冷缓存：user.home 指到临时目录（必须在触碰 ServiceCreator/HanimeNetwork 之前）
        val coldHome = File("/tmp/han1me_perf_probe").apply {
            deleteRecursively()
            mkdirs()
        }
        System.setProperty("user.home", coldHome.absolutePath)
        System.setProperty("java.net.useSystemProxies", "true")

        runCatching { SettingsRepository.install(InMemorySettingsStore()) }
        PlayerTrace.resetForTest()
        PlayerTrace.begin("perf-probe")

        // 2. 环境快照：代理/直连走了哪条路
        runCatching {
            val proxies = ProxySelector.getDefault().select(URI("https://hanime1.me/"))
            println("[perf-probe] JVM 选定代理: $proxies")
        }.onFailure { println("[perf-probe] 代理探测失败: ${it.message}") }
        println("[perf-probe] useDoH=${SettingsRepository.useDoH} proxyType=${SettingsRepository.proxyType}")

        // 3. 首页（App 启动路径，交首次连接学费）
        var t0 = System.currentTimeMillis()
        runCatching {
            val resp = HanimeNetwork.hanimeService.getHomePage(SettingsRepository.homeUrl)
            val body = resp.bodyAsText()
            println("[perf-probe] home status=${resp.status.value} len=${body.length}")
            Regex("watch\\?v=(\\d+)").findAll(body).map { it.groupValues[1] }.distinct().firstOrNull()?.let {
                println("[perf-probe] 首页首个视频码=$it（供 App 探针用）")
            }
        }.onFailure { println("[perf-probe] home 失败: ${it::class.simpleName} ${it.message}") }
        println("[perf-probe] == GET /home（冷连接+冷缓存）耗时 ${System.currentTimeMillis() - t0}ms ==")

        // 4. watch 页（= 点第一个视频卡片；code 取自用户真实缓存里的近期观看）
        t0 = System.currentTimeMillis()
        val states = withTimeoutOrNull(240_000) {
            NetworkRepo.getHanimeVideo("408112").toList()
        }
        if (states == null) {
            println("[perf-probe] watch 240s 未结束（网络超时）")
        } else {
            println("[perf-probe] 状态序列=${states.map { it::class.simpleName }}")
            val success = states.filterIsInstance<VideoLoadingState.Success<*>>().firstOrNull()
            if (success != null) {
                val info = success.info as lovehan1me.core.domain.model.HanimeVideo
                println("[perf-probe] 成功: 标题=${info.title.take(20)}… 画质数=${info.videoUrls.size}")
            }
        }
        println("[perf-probe] == GET watch?v=408112（冷缓存）耗时 ${System.currentTimeMillis() - t0}ms ==")

        // 5. 二次打开（对照：应当命中 OkHttp 磁盘缓存秒回）
        t0 = System.currentTimeMillis()
        val states2 = withTimeoutOrNull(30_000) {
            NetworkRepo.getHanimeVideo("408112").toList()
        }
        println("[perf-probe] 二次状态=${states2?.map { it::class.simpleName }} 耗时 ${System.currentTimeMillis() - t0}ms")

        PlayerTrace.summary()
        println("[perf-probe] 冷缓存目录: ${coldHome.absolutePath}/.lovehan1me/http_cache")
    }
}
