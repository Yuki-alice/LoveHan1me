package lovehan1me.data.network

import lovehan1me.core.util.LogUtil
import lovehan1me.data.SettingsRepository
import lovehan1me.core.util.CookieString
import lovehan1me.core.util.toLoginCookieList
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 用於管理 Cookie。
 *
 * #issue-71: 我竟然栽倒在 Cookie 管理上好幾年了！你去看我以前的管理方式，
 * 是完全錯誤的，竟然還能維持應用正常運行，太離譜了！怪不得切換簡體繁體一直不起作用！
 *
 * @project LoveHan1me
 * @author Yenaly Liew（上游原作者，见 NOTICE）
 * @time 2024/03/13 013 15:20
 */
class HCookieJar : CookieJar {

    companion object {
        @JvmStatic
        val cookieMap: MutableMap<String, MutableList<Cookie>> = mutableMapOf()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = mutableListOf<Cookie>()
        // cf_clearance 只认持久化那一份：它由验证窗写入、由 403 作废。内存里再留一份就会
        // 绕过失效逻辑，让请求一直拿着死钥匙撞 403（表现即"验证过了还是不行"）。
        cookieMap[host]?.filterNot { it.name == CF_CLEARANCE_NAME }?.let { cookies.addAll(it) }

        cookies.addAll(CookieString(SettingsRepository.current.loginCookie).toLoginCookieList(host))
        SettingsRepository.cfCookieFor(host)?.let { clearance ->
            cookies.addAll(CookieString(clearance).toLoginCookieList(host))
        }

        // 只报数量与键名：值里是 hanime1_session / XSRF-TOKEN / cf_clearance 等凭据，
        // 原样打出来既刷屏（单行数 KB）又等于泄漏。
        LogUtil.d(
            "HCookieJar",
            "loadForRequest for $host: ${cookies.size} 条 [${cookies.joinToString { it.name }}]",
        )

        return cookies
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // 合并而非覆盖：直接替换会丢掉内存里的 cf_clearance（CDP 刚写回的），
        // 下一次请求就只剩 DataStore 路径；若那次恰好 host 不一致即裸奔。
        // 同名（name+domain+path）以响应值为准，其余保留。
        val merged = cookieMap[url.host]?.toMutableList() ?: mutableListOf()
        for (fresh in cookies) {
            merged.removeAll { it.name == fresh.name && it.domain == fresh.domain && it.path == fresh.path }
            merged.add(fresh)
        }
        merged.removeAll { it.name == "user_lang" }
        merged += CookieString(SettingsRepository.current.loginCookie).toLoginCookieList(url.host)
        cookieMap[url.host] = merged
    }
}
