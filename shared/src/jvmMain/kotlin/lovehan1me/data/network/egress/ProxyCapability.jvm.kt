package lovehan1me.data.network.egress

import java.net.Proxy
import java.net.URI
import java.net.ProxySelector as JavaProxySelector

/**
 * JVM（Android + 桌面）的系统代理解析。
 *
 * 刻意**只读 `ProxySelector.getDefault()`**，不碰 `SettingsRepository` —— 这个函数会被
 * 设置层的"条件默认值"在解析设置的过程中调用，读设置会绕回去。
 *
 * 桌面的 `DefaultProxySelector` 需要 `-Djava.net.useSystemProxies=true` 才会去查系统代理，
 * 该参数由 desktopApp 的 jvmArgs 与 Main.kt 各自兜一次（两处都要在类加载前生效）。
 */
actual fun platformSystemProxyUsable(): Boolean {
    val uri = runCatching { URI("https://hanime1.me/") }.getOrNull() ?: return false
    val selected = runCatching { JavaProxySelector.getDefault()?.select(uri) }.getOrNull() ?: return false
    return selected.any { it.type() != Proxy.Type.DIRECT }
}
