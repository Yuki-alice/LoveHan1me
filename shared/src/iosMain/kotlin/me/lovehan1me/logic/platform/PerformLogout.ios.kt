package me.lovehan1me.logic.platform

import me.lovehan1me.logout

// M5：改调共享 logout()。此前此处的内联实现遗漏了 HCookieJar.cookieMap.clear()，
// 导致 iOS 登出后内存 Cookie 残留、直到重启才真正登出。
actual suspend fun performAccountLogout() {
    logout()
}
