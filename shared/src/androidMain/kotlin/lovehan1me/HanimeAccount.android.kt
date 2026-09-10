package lovehan1me

import android.webkit.CookieManager

actual fun clearWebCookies() {
    CookieManager.getInstance().removeAllCookies(null)
}
