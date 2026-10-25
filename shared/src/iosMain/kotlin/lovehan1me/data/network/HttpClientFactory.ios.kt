package lovehan1me.data.network

import io.ktor.client.HttpClient
import lovehan1me.data.network.egress.EgressPurpose

/**
 * iOS Darwin 工厂（Phase 3 超时对齐 JVM 档位）。
 *
 * - 浏览/getchu（Api）：request 60s / connect 15s / socket 30s，对齐
 *   jvmMain `ServiceCreator.hClient` 的 call 60s / connect 15s / read 30s；
 * - 下载走各端专路（Android WorkManager / 桌面与 iOS 自建 client），不在此列；
 * - 第三方干净 client：不动（本来就不该有预算，不同业务）。
 *
 * 装配细节（ECH/Cookie/重试/会话钩子）统一在 [createIosTransport]（网络优化 1.1）。
 */
actual fun createHanimeHttpClient(): HttpClient = createIosTransport(
    purpose = EgressPurpose.Api,
    requestTimeoutMillis = 60_000,
    connectTimeoutMillis = 15_000,
    socketTimeoutMillis = 30_000,
    retry = true,
)

actual fun createGetchuHttpClient(): HttpClient = createIosTransport(
    purpose = EgressPurpose.Api,
    requestTimeoutMillis = 60_000,
    connectTimeoutMillis = 15_000,
    socketTimeoutMillis = 30_000,
    retry = true,
)

actual fun createPlainHttpClient(): HttpClient = createIosTransport(
    purpose = EgressPurpose.Api,
    withEchGate = false,
    withCookies = false,
    requestTimeoutMillis = 15_000,
)
