package lovehan1me.data

import android.webkit.CookieManager

actual fun clearWebCookies() {
    CookieManager.getInstance().removeAllCookies(null)
}
