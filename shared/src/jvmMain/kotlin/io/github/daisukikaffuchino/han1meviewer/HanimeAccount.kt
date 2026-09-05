package io.github.daisukikaffuchino.han1meviewer

import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.logic.network.HCookieJar

/**
 * P6d-4：自 :app HanimeManager.kt 下沉的登录态操作（NetworkSettingsRoute 域名切换后登出依赖）。
 * WebView Cookie 清理为平台差异，由 [clearWebCookies] expect 承担。
 */
expect fun clearWebCookies()

suspend fun logout() {
    SettingsRepository.update { it.copy(isAlreadyLogin = false, loginCookie = EMPTY_STRING, savedUserId = EMPTY_STRING) }
    HCookieJar.cookieMap.clear()
    clearWebCookies()
}

suspend fun login(cookies: String) =
    SettingsRepository.update { it.copy(isAlreadyLogin = true, loginCookie = cookies) }

suspend fun login(cookies: List<String>) {
    login(cookies.joinToString(";") {
        it.substringBefore(';')
    })
}
