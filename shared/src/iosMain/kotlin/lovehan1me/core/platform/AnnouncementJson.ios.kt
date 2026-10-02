package lovehan1me.core.platform

/**
 * iOS 侧不实现远端公告拉取，与 [performUpdateJsonRequest] 保持同一平台面。
 *
 * 根因不是「iOS 没网络」（`HttpClientFactory.ios.kt` 有完整的 Darwin 栈），
 * 而是本仓的**第三方直连客户端** `createThirdPartyClient`（带 DNS 覆盖与 UA，
 * 不走 ECH 网关）只有 jvm 实现。直接换 `createPlainHttpClient` 就能编译通过，
 * 但那会绕开代理配置 —— 配了代理的用户会连不上，属于把「功能缺失」换成「静默走错出口」。
 * 要做就先把第三方 client 的 iOS 实现补齐，见 `docs/decisions.md`。
 */
actual suspend fun performAnnouncementJsonRequest(): String? = null
