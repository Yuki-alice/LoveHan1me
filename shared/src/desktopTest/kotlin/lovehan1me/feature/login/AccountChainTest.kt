package lovehan1me.feature.login

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.data.NetworkRepo
import lovehan1me.data.SettingsRepository
import lovehan1me.data.login
import lovehan1me.data.logout
import lovehan1me.data.network.HCookieJar
import lovehan1me.data.network.HanimeNetwork
import lovehan1me.site.hanime1.Parser
import okhttp3.Cookie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 账号链路：会话写入/清理（离线，可跑）+ 真实表单登录（live，默认跑、缺凭据 skip）。
 *
 * 为什么拆两条：
 * - 离线条：`login()` 写入 DataStore 状态、`logout()` 清状态 + 清内存 Cookie，
 *   纯本地逻辑，在哪都能断言（内存 store，不碰 `~/.lovehan1me` 真实文件）。
 * - live 条：走与 [FormLoginScreen] 完全相同的 `NetworkRepo.login`
 *  （GET 登录页取 CSRF → POST → 再 GET 验 404 → 取 Set-Cookie），
 *   再附带验一次"内存 Cookie 会话有效"（HCookieJar 自动接住 Set-Cookie，
 *   重进 /login 应 404）。
 *
 * 跑法（zsh，注意先 --stop 让环境变量传进守护进程）：
 * ```
 * HAN1ME_LOGIN_EMAIL='xxx@qq.com' HAN1ME_LOGIN_PASSWORD='...' ./gradlew --stop
 * HAN1ME_LOGIN_EMAIL='xxx@qq.com' HAN1ME_LOGIN_PASSWORD='...' ./gradlew :shared:desktopTest --tests '*AccountChain*'
 * ```
 *
 * 安全：只读两个环境变量；日志只打邮箱掩码与 Cookie **名**，
 * 密码与 Cookie 值永不打印、不落盘；live 条不调 persistLogin，
 * DataStore 写入零副作用（内存 store）。
 *
 * 已知限制：Cloudflare 会在边缘按 IP 拦截（如云主机出口 IP），此时首个
 * GET 即 403 → token 解析抛 ParseException → 收敛为 Error（优雅失败，
 * 不崩）。这条在那种网络下会红，红了先看打印的状态序列与 HTTP 状态，
 * 不要直接当登录逻辑 bug 修。
 */
private class InMemorySettingsStore : SettingsStore {
    private val state = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = state
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

private fun installTestStore() {
    // 同一 JVM 跑多个测试类时只允许 install 一次，重复的忽略。
    runCatching { SettingsRepository.install(InMemorySettingsStore()) }
}

private fun maskEmail(email: String): String {
    val domain = email.substringAfter('@', missingDelimiterValue = "***")
    return email.take(1) + "***@" + domain
}

class AccountSessionOfflineTest {

    @Test
    fun `登录写入会话_登出清理干净`() = runBlocking {
        installTestStore()

        assertFalse(SettingsRepository.isAlreadyLogin, "初始应未登录")

        login(
            listOf(
                "hanime1_session=dummy-session-value; expires=Fri, 01-Jan-2038 00:00:00 GMT; path=/",
                "XSRF-TOKEN=dummy-xsrf; expires=Fri, 01-Jan-2038 00:00:00 GMT; path=/",
            ),
        )
        assertTrue(SettingsRepository.isAlreadyLogin, "login() 后应已登录")
        assertEquals(
            "hanime1_session=dummy-session-value;XSRF-TOKEN=dummy-xsrf",
            SettingsRepository.current.loginCookie,
            "loginCookie 应为各条 name=value 以分号拼接",
        )

        // 往内存 jar 里放一块饼干，模拟登录态下的请求 Cookie。
        HCookieJar.cookieMap["hanime1.me"] = mutableListOf(
            Cookie.Builder().name("hanime1_session").value("dummy-session-value")
                .domain("hanime1.me").build(),
        )

        logout()

        assertFalse(SettingsRepository.isAlreadyLogin, "logout() 后应未登录")
        assertEquals("", SettingsRepository.current.loginCookie, "logout() 应清 loginCookie")
        assertEquals("", SettingsRepository.current.savedUserId, "logout() 应清 savedUserId")
        assertTrue(
            HCookieJar.cookieMap["hanime1.me"].isNullOrEmpty(),
            "logout() 应清内存 Cookie（HCookieJar.cookieMap）",
        )
        println("[offline] 登录写入 + 登出清理全通")
    }
}

class FormLoginLiveTest {

    @Test
    fun `真实表单登录并校验会话`() = runBlocking {
        val email = System.getenv("HAN1ME_LOGIN_EMAIL")?.takeIf { it.isNotBlank() }
        val password = System.getenv("HAN1ME_LOGIN_PASSWORD")?.takeIf { it.isNotBlank() }
        if (email == null || password == null) {
            println("[skip] 未设 HAN1ME_LOGIN_EMAIL / HAN1ME_LOGIN_PASSWORD —— 跳过真实登录（见文件头 KDoc）")
            return@runBlocking
        }
        installTestStore()
        HCookieJar.cookieMap.clear()
        println("[live] 账号=${maskEmail(email)}，开始真实表单登录")

        val states = withTimeoutOrNull(120_000) {
            NetworkRepo.login(email, password).toList()
        }
        assertNotNull(states, "120s 内登录流未结束（网络超时）")
        println("[live] 状态序列=${states.map { it::class.simpleName }}")

        val error = states.filterIsInstance<WebsiteState.Error>().firstOrNull()
        if (error != null) {
            println("[live] Error 类型=${error.throwable::class.simpleName} message=${error.throwable.message}")
            runStepProbe(email, password)
        }
        val success = states.filterIsInstance<WebsiteState.Success<*>>().firstOrNull()
        assertNotNull(success, "登录未成功（先看上面的 Error 类型与 message；若是 CF 边缘拦截见文件头 KDoc）")

        @Suppress("UNCHECKED_CAST")
        val setCookies = success.info as List<String>
        // 只打 Cookie 名，值脱敏。
        val names = setCookies.map { it.substringBefore(';').substringBefore('=') }
        println("[live] Set-Cookie 条数=${setCookies.size} 名=$names")
        assertTrue(names.any { it == "hanime1_session" }, "Set-Cookie 应含 hanime1_session")

        // 会话有效性：HCookieJar 已自动接住 Set-Cookie，重进 /login 已登录应 404。
        val again = HanimeNetwork.hanimeService.getLoginPage()
        println("[live] 登录后重进 /login 状态=${again.status.value}")
        assertEquals(404, again.status.value, "已登录重进 /login 应 404（与 NetworkRepo.login 内判定一致）")
        println("[live] 真实登录 + 会话校验全通")
    }

    /**
     * 逐步诊断探针：把 NetworkRepo.login 内部三步拆开打状态码，
     * 定位到底是 CF 拦截 / token 缺失 / 凭据被拒 / 站点判定语义变更。
     * 只打状态码与 Cookie 名，不打任何值。
     */
    private suspend fun runStepProbe(email: String, password: String) {
        println("[probe] 开始逐步诊断")
        val p1 = HanimeNetwork.hanimeService.getLoginPage()
        println("[probe] 1) GET /login status=${p1.status.value}")
        if (!p1.status.isSuccess()) {
            println("[probe] 首 GET 即非 2xx（CF 边缘拦截或源站拒绝），停止诊断")
            return
        }
        val body = p1.bodyAsText()
        val token = runCatching { Parser.extractTokenFromLoginPage(body) }.getOrNull()
        println("[probe] 2) token=${if (token != null) "OK" else "MISSING"} bodyLen=${body.length}")
        if (token == null) {
            println("[probe] 登录页无 _token（页面结构变了或被顶替），停止诊断")
            return
        }
        val post = HanimeNetwork.hanimeService.login(token, email, password)
        val postCookies = post.headers.getAll("Set-Cookie").orEmpty()
            .map { it.substringBefore(';').substringBefore('=') }
        println("[probe] 3) POST /login status=${post.status.value} setCookieNames=$postCookies")
        val g2 = HanimeNetwork.hanimeService.getLoginPage()
        println("[probe] 4) 再 GET /login status=${g2.status.value}（404=已登录，200=凭据被拒，其他=站点语义变了）")
    }
}
