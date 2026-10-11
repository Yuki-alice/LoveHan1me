package lovehan1me.data.network

/**
 * iOS 壳起服网关的参数入口（B4-3b）。
 *
 * Swift 侧 `EchGateBootstrap` 调这里取**生效值**（用户覆盖已落到里面，
 * 未覆盖时回落到与网关默认值一致的常量），而不是在 Swift 里再硬编码一份 —
 * 此前 DoH/超时/ech-b64 全写死在 Swift 里，用户在设置页改的东西对 iOS
 * 网关无效（见任务分解 §五 B4-3 阻断说明）。
 *
 * 与 [EchGatePortReporter] 同模式：`EchGateStartArgs.shared.xxx()`。
 * 导出给 Swift 的只有 String/Long（`timeoutMs` 到 Swift 是 `Int64`），
 * 无重载、无默认值 —— Kotlin/Native 的 Swift 导出对这两种最稳。
 */
object EchGateStartArgs {

    /** 起服 `dohURL` 形参：用户覆盖，无则网关默认。 */
    fun dohUrl(): String = echGateDohUrl() ?: ECH_GATE_DOH_URL_DEFAULT

    /** 起服 `timeoutMs` 形参：用户覆盖的秒数换算成毫秒。 */
    fun timeoutMs(): Long = echGateTimeoutSec() * 1000L

    /** 起服 `echB64` 形参：空 = 网关经 DoH 自取。 */
    fun echB64(): String = echGateEchB64() ?: ""

    /** 起服 `upstreamProxy` 形参（B2-4/D4-b）：空 = 直连模式。 */
    fun upstreamProxy(): String = echGateUpstreamProxy() ?: ""
}
