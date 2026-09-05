package io.github.daisukikaffuchino.han1meviewer

import android.webkit.CookieManager

actual fun clearWebCookies() {
    CookieManager.getInstance().removeAllCookies(null)
}
