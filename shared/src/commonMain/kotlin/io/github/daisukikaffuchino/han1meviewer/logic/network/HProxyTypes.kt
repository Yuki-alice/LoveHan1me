package io.github.daisukikaffuchino.han1meviewer.logic.network

/**
 * P6d-4：代理类型常量上移 commonMain（原 :app 时代定义于 HProxySelector companion）。
 *
 * 值与存储语义不变——`SettingsRepository.proxyType` 存的就是这些 Int。
 * jvmMain 的 HProxySelector.companion 引用本对象保持调用点零改动；
 * commonMain 侧 UI（NetworkSettingsScreen 的 ProxyType enum）直接引用本对象。
 */
object HProxyTypes {
    const val TYPE_DIRECT = 0
    const val TYPE_SYSTEM = 1
    const val TYPE_HTTP = 2
    const val TYPE_SOCKS = 3
}
