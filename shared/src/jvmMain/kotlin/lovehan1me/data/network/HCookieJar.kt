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
        val loginCookies = CookieString(SettingsRepository.current.loginCookie).toLoginCookieList(host)
        // cf_clearance 只认持久化那一份：它由验证窗写入、由 403 作废。内存里再留一份就会
        // 绕过失效逻辑，让请求一直拿着死钥匙撞 403（表现即"验证过了还是不行"）。
        val cookies = mutableListOf<Cookie>()
        cookieMap[host]?.filterNot { it.name == CF_CLEARANCE_NAME }?.let { cookies.addAll(it) }

        cookies.addAll(loginCookies)
        SettingsRepository.cfCookieFor(host)?.let { clearance ->
            cookies.addAll(CookieString(clearance).toLoginCookieList(host))
        }

        // 同键（name+domain+path）只发第一份：map 在前、DataStore 在后 =
        // 服务端取首份时响应带来的新鲜值优先；且无论哪一侧重复累积，发出字节数都有上界。
        // （2026-10-09 全站 400 的第二道闸：存侧无界累积是根因，本处保证发出侧同样有界。）
        val seen = HashSet<Triple<String, String, String>>()
        val deduped = cookies.filter { seen.add(Triple(it.name, it.domain, it.path)) }

        // 只报数量、键名与总字节：值里是 hanime1_session / XSRF-TOKEN / cf_clearance 等凭据，
        // 原样打出来既刷屏（单行数 KB）又等于泄漏；字节数是下次"超限"最直接的证据。
        LogUtil.d(
            "HCookieJar",
            "loadForRequest for $host: ${deduped.size} 条 " +
                "[${deduped.joinToString { it.name }}] " +
                "≈${deduped.sumOf { it.name.length + it.value.length + 1 }}字节",
        )

        return deduped
    }

    private fun cookieKey(cookie: Cookie) = Triple(cookie.name, cookie.domain, cookie.path)

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // 合并而非覆盖：直接替换会丢掉内存里的 cf_clearance（CDP 刚写回的），
        // 下一次请求就只剩 DataStore 路径；若那次恰好 host 不一致即裸奔。
        // 同名（name+domain+path）以响应值为准，其余保留。
        val merged = cookieMap[url.host]?.toMutableList() ?: mutableListOf()
        for (fresh in cookies) {
            merged.removeAll { it.name == fresh.name && it.domain == fresh.domain && it.path == fresh.path }
            merged.add(fresh)
        }
        // loginCookie 对账（2026-10-09 全站 400 "Header Or Cookie Too Large" 的根因修复）：
        // 上面只对"本响应自带"的同键去重，而 DataStore 那份此前是无条件 append ——
        // 每个响应攒一份完整 loginCookie，发出头线性增长直到 nginx 拒收。
        // （旧 `removeAll { user_lang }` 只堵住了 user_lang 这一条名字，
        //  session 系照漏不误 —— 这正是此前"单名不爆、总量爆"。）
        // 现按名对账：响应带了谁就信谁的（服务端旋转优先），没带的用 DataStore 补一份；
        // 两种来源各 key 至多一份，存储侧从此有界。
        val loginCookies = CookieString(SettingsRepository.current.loginCookie).toLoginCookieList(url.host)
        val loginNames = loginCookies.mapTo(HashSet()) { it.name }
        val carried = cookies.filter { it.name in loginNames }.mapTo(HashSet(), ::cookieKey)
        merged.removeAll { it.name in loginNames && cookieKey(it) !in carried }
        val present = merged.mapTo(HashSet(), ::cookieKey)
        merged += loginCookies.filterNot { cookieKey(it) in present }
        // 过期持久 cookie 一并清掉：死了还占着名额，纯属 dead weight。
        merged.removeAll { it.persistent && it.expiresAt < System.currentTimeMillis() }
        cookieMap[url.host] = merged
    }
}
