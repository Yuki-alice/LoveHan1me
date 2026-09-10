package lovehan1me.data

import lovehan1me.data.SettingsRepository
import lovehan1me.core.constant.EMPTY_STRING

/**
 * M5：登录态操作，自 jvmMain 上移至 commonMain，三端共享。
 *
 * 此前这套 API 只在 jvmMain（android+desktop），iOS 编译不到，导致 iOS 只能
 * 手动填 Cookie 且 `performAccountLogout` 复制了一份遗漏内存 Cookie 清理的实现。
 * 表单登录（`NetworkRepo.login`）本就在 commonMain，下沉后 iOS 可走与网站一致的
 * 邮箱+密码登录路径。
 *
 * 两类清理都是平台差异：
 * - [clearMemoryCookies]：HTTP 客户端内存 Cookie（jvm = HCookieJar）
 * - [clearWebCookies]：WebView Cookie（仅 Android 有 WebView 登录路径）
 */
expect fun clearMemoryCookies()

expect fun clearWebCookies()

suspend fun logout() {
    SettingsRepository.update {
        it.copy(
            isAlreadyLogin = false,
            loginCookie = EMPTY_STRING,
            savedUserId = EMPTY_STRING,
        )
    }
    clearMemoryCookies()
    clearWebCookies()
}

suspend fun login(cookies: String) =
    SettingsRepository.update { it.copy(isAlreadyLogin = true, loginCookie = cookies) }

suspend fun login(cookies: List<String>) {
    login(cookies.joinToString(";") {
        it.substringBefore(';')
    })
}
