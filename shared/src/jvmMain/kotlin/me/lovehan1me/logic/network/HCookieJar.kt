package me.lovehan1me.logic.network

import me.lovehan1me.utils.LogUtil
import me.lovehan1me.logic.SettingsRepository
import me.lovehan1me.util.CookieString
import me.lovehan1me.util.toLoginCookieList
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 用於管理 Cookie。
 *
 * #issue-71: 我竟然栽倒在 Cookie 管理上好幾年了！你去看我以前的管理方式，
 * 是完全錯誤的，竟然還能維持應用正常運行，太離譜了！怪不得切換簡體繁體一直不起作用！
 *
 * @project Han1meViewer
 * @author Yenaly Liew
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
        cookieMap[host]?.let { cookies.addAll(it) }

        cookies.addAll(CookieString(SettingsRepository.current.loginCookie).toLoginCookieList(host))
        if (SettingsRepository.cloudFlareCookieHost == host) {
            cookies.addAll(CookieString(SettingsRepository.current.cloudFlareCookie).toLoginCookieList(host))
        }

        LogUtil.d("HCookieJar", "loadForRequest for $host: $cookies")

        return cookies
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieMap[url.host] = cookies.toMutableList().also {
            it += CookieString(SettingsRepository.current.loginCookie).toLoginCookieList(url.host)
        }
    }
}
