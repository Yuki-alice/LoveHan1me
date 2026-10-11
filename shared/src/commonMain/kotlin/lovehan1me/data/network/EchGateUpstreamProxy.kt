package lovehan1me.data.network

import lovehan1me.data.SettingsRepository

/**
 * 由当前代理设置推出网关的**上游代理**参数（B2-4/D4-b）。
 *
 * 只认**显式**的 HTTP / SOCKS 代理：`System` 档应用侧拿不到具体地址、`Direct` 档本就没有代理
 * —— 两者一律返回 null（网关走直连，不进入代理模式）。返回形如 `http://host:port` /
 * `socks5://host:port`，与 Go 侧 `Config.UpstreamProxy` 同形状。
 *
 * 为什么放 commonMain：三端（桌面 spawn 参数 / Android、iOS gomobile 入口）都该用同一份判定，
 * 平台只负责把结果传下去 —— 与"判定不进平台分叉"的既有约定一致。
 */
fun echGateUpstreamProxy(): String? {
    val type = runCatching { SettingsRepository.proxyType }.getOrNull() ?: return null
    val ip = runCatching { SettingsRepository.proxyIp }.getOrNull().orEmpty()
    val port = runCatching { SettingsRepository.proxyPort }.getOrNull() ?: -1
    if (ip.isBlank() || port !in 1..65535) return null
    return when (type) {
        HProxyTypes.TYPE_HTTP -> "http://$ip:$port"
        HProxyTypes.TYPE_SOCKS -> "socks5://$ip:$port"
        else -> null
    }
}
