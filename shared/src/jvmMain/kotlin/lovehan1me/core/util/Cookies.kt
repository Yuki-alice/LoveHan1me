package lovehan1me.core.util

import lovehan1me.core.util.LogUtil
import lovehan1me.data.SettingsRepository
import okhttp3.Cookie

@JvmInline
value class CookieString(val cookie: String)

/**
 * 主要用於 [HCookieJar][lovehan1me.data.network.HCookieJar]，最好不要用到其他地方。
 */
fun CookieString.toLoginCookieList(domain: String): List<Cookie> {
    val cookieList = mutableListOf<Cookie>().also {
        it += preferencesCookieList(domain)
    }
    cookie.split(';').forEach { cookie ->
        if (cookie.isNotBlank()) {
            val name = cookie.substringBefore('=').trim()
            val value = cookie.substringAfter('=').trim()
            val cleanedName = name.filter { it.code in 0x20..0x7E && it != '\n' && it != '\r' }
            val cleanValue = value.filter { it.code in 0x20..0x7E && it != '\n' && it != '\r' }
            if (cleanedName.isNotEmpty()) {
                try {
                    cookieList += Cookie.Builder()
                        .domain(domain)
                        .name(cleanedName)
                        .value(cleanValue)
                        .build()
                } catch (e: IllegalArgumentException) {
                    // ⚠️ 只报键名与原因，**不回显值**：cookie 值里是 session / XSRF 令牌，
                    // 打进终端或日志等于把它们又抄了一遍（且这里还是 WARN 级，门槛拦不住）。
                    LogUtil.w(
                        "CookieString",
                        "无效Cookie: $cleanedName（值已省略）, error=${e.message}"
                    )
                }
            } else {
                LogUtil.w("CookieString", "无效键值（内容已省略，长度=${cookie.length}）")
            }
        }
    }
    return cookieList.also {
        // 只报数量与键名：cookie 的值是凭据，任何日志级别都不该出现在终端里。
        LogUtil.d(
            "CookieString",
            "toCookieList($domain): ${it.size} 条 [${it.joinToString { c -> c.name }}]",
        )
    }
}

/**
 * 每次退出登入後都會清除cookie，但是這樣可能會清除掉很多保存在cookie中的偏好，比如影片語言之類。
 *
 * 讓[preferencesCookieList]成爲 存在偏好設置 但不存在個人信息 的[emptyList]
 */
private fun preferencesCookieList(domain: String): List<Cookie> {
    val videoLanguage = SettingsRepository.videoLanguage
    val videoLanguageCookie = Cookie.Builder().domain(domain)
        .name("user_lang")
        .value(videoLanguage)
        .build()
    return listOf(videoLanguageCookie)
}
