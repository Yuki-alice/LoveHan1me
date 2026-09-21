package lovehan1me.site

import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.io.File
import java.net.ProxySelector
import java.net.URI
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.HanimeNetwork

/**
 * G2-1b-2 抓包工具（dev-only）：把作者页与系列清单页的实时 HTML 落到 `.workbuddy/`，
 * 供解析开发与夹具回归用。不 assert 页面结构（站点随时会变），只保证"抓得到"。
 *
 * 用法：`./gradlew :shared:desktopTest --tests '*SitePageCapture*'`
 * 产物：`.workbuddy/g2b2_user.html`、`.workbuddy/g2b2_playlist.html`（gitignore，不入库）。
 */
class SitePageCaptureLiveTest {

    private class InMemorySettingsStore : SettingsStore {
        private val state = MutableStateFlow(AppSettings())
        override val settings: StateFlow<AppSettings> = state
        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            state.value = transform(state.value)
        }
    }

    private fun outDir(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) break
            dir = dir.parentFile
        }
        return File(dir ?: File("."), ".workbuddy").apply { mkdirs() }
    }

    @Test
    fun `抓作者页与系列清单页`() = runBlocking {
        System.setProperty("java.net.useSystemProxies", "true")
        runCatching { SettingsRepository.install(InMemorySettingsStore()) }
        runCatching {
            val proxies = ProxySelector.getDefault().select(URI("https://hanime1.me/"))
            println("[capture] JVM 选定代理: $proxies")
        }

        val targets = listOf(
            "g2b2_home.html" to "https://hanime1.me/",
            "g2b2_user.html" to "https://hanime1.me/user/367299",
            "g2b2_user_367290.html" to "https://hanime1.me/user/367290",
            "g2b2_user_367290_playlists.html" to "https://hanime1.me/user/367290/playlists",
            "g2b2_user_uploaded.html" to "https://hanime1.me/user/367299/uploaded",
            "g2b2_user_playlists.html" to "https://hanime1.me/user/367299/playlists",
            "g2b2_user_playlists_popular.html" to "https://hanime1.me/user/367299/playlists?sort=popular",
            "g2b2_playlist.html" to "https://hanime1.me/playlist?list=976998&sort=latest",
        )
        for ((file, url) in targets) {
            val body = withTimeoutOrNull(120_000) {
                val resp = HanimeNetwork.hanimeService.getHomePage(url)
                println("[capture] $url status=${resp.status.value}")
                if (!resp.status.isSuccess()) return@withTimeoutOrNull null
                resp.bodyAsText()
            }
            if (body == null) {
                println("[capture] $url 抓取失败（超时或非 2xx，疑似 CF 墙）")
            } else {
                File(outDir(), file).writeText(body)
                println("[capture] $url len=${body.length} -> .workbuddy/$file")
                println("[capture] watch链接数=${Regex("watch\\?v=(\\d+)").findAll(body).count()} " +
                    "user链接数=${Regex("/user/(\\d+)").findAll(body).count()} " +
                    "playlist链接数=${Regex("playlist\\?list=(\\d+)").findAll(body).count()}")
            }
        }
    }
}
