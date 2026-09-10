package me.lovehan1me

import me.lovehan1me.logic.network.HCookieJar

// M5：自 jvmMain HanimeAccount.kt 拆出的平台 actual（expect 已上移 commonMain）。
// 注意：jvmMain 是 android+desktop 的共同父源集，clearWebCookies 的 actual
// 各自留在 androidMain/desktopMain（Android 要清 WebView，桌面为空），不可在此合并。
actual fun clearMemoryCookies() {
    HCookieJar.cookieMap.clear()
}
