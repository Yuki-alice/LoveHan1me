package lovehan1me.data.network

import io.ktor.client.HttpClientConfig
import io.ktor.http.Url
import lovehan1me.core.util.LogUtil
import lovehan1me.data.network.egress.EgressPurpose

/**
 * iOS 侧的 ECH 网关接入（Darwin 引擎走 Ktor 插件，见 [EchGateClientPlugin]）。
 *
 * Cookie 由本文件的 provider 按**原域名**取（内存桥 + DataStore 登录态 +
 * clearance，与 [BridgeCookiesStorage] 同源）；插件装在 `HttpCookies` 之前，
 * 改写后 storage 对回环 host 短路（见 [BridgeCookiesStorage.get]），
 * 同一条 Cookie 不会发两遍。
 *
 * 是否改写由调度器裁决（插件内部读快照）：用户关掉、进程没跑、或该域熔断一律不放行，
 * 与 jvm 侧 EchGateInterceptor 是同一份计划与同一个注册表。
 *
 * 网关运行时由 Swift 壳起服、`EchGatePortReporter` 回填端口：[EchGate.port] 为 -1
 * 时零改动，开着开关也无害——现有机制即兜底。
 */
internal fun HttpClientConfig<*>.installEchGate(
    withCookies: Boolean = true,
    defaultPurpose: EgressPurpose = EgressPurpose.Api,
) {
    install(EchGateClientPlugin) {
        // 图片链传 false：图床不需要登录态，把 hanime1_session 发过去只是平白泄漏凭据。
        if (withCookies) cookieHeaderProvider = ::echCookieHeader
        this.defaultPurpose = defaultPurpose
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

/**
 * iOS 侧 no-op：网关**有**运行时，只是它的生命周期归 Swift 壳管
 * （`EchGateBootstrap.start()` 无条件起服 → `EchGatePortReporter.setPort` 回填端口）。
 * Kotlin 侧没有"拉起"这个动作可做，留空是对的分发，不是缺实现。
 */
actual fun ensureEchGateway() {
}
