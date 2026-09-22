package lovehan1me.data.network

import io.ktor.client.HttpClientConfig
import io.ktor.http.Url
import lovehan1me.core.util.LogUtil
import lovehan1me.data.SettingsRepository

/**
 * iOS 侧的 ECH 网关接入（Darwin 引擎走 Ktor 插件，见 [EchGateClientPlugin]）。
 *
 * Cookie 由本文件的 provider 按**原域名**取（内存桥 + DataStore 登录态 +
 * clearance，与 [BridgeCookiesStorage] 同源）；插件装在 `HttpCookies` 之前，
 * 改写后 storage 对回环 host 短路（见 [BridgeCookiesStorage.get]），
 * 同一条 Cookie 不会发两遍。
 *
 * 网关运行时待接入：`portProvider` 读 [EchGate.port]，为 -1 时零改动，
 * 开着开关也无害——现有机制即兜底。
 */
internal fun HttpClientConfig<*>.installEchGate() {
    install(EchGateClientPlugin) {
        // Swift 侧自启网关（无设置不启动的概念），是否改写以后端设置为准：
        // 用户关掉开关时回 -1，插件零改动，等价于网关不存在。
        portProvider = {
            runCatching { if (SettingsRepository.useEchGate) EchGate.port else -1 }
                .getOrDefault(-1)
        }
        cookieHeaderProvider = ::echCookieHeader
        logger = { LogUtil.d("EchGate", it) }
    }
}

/** 按原 URL 取 Cookie 请求头（与 BridgeCookiesStorage.get 同源，网关改写专用）。 */
internal suspend fun echCookieHeader(originalUrl: Url): String? {
    val host = originalUrl.host
    val parts = mutableListOf<String>()
    IosCookieBridge.snapshot()
        .filterKeys { it != CF_CLEARANCE_NAME }
        .forEach { (name, value) -> parts += "$name=$value" }
    IosCookieBridge.loginCookiesFor(host).forEach { parts += "${it.name}=${it.value}" }
    IosCookieBridge.cloudFlareCookiesFor(host).forEach { parts += "${it.name}=${it.value}" }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("; ")
}

/** iOS 无网关运行时，no-op（运行时接入后在此拉起 gomobile 实例）。 */
actual fun ensureEchGateway() {
}
