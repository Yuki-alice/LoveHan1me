package lovehan1me.data.network

import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.egress.EgressPlanner
import lovehan1me.feature.player.PlayerMpvOptions
import lovehan1me.feature.player.PlayerMpvOptionsProvider
import lovehan1me.feature.player.PlayerNetworkConfig

/**
 * `:video:engine` 注入接口的 `:shared` 实现（Gate3-P1）。
 *
 * 平台差异只在网络配置（UA/代理实现各端不同），mpv 选项快照是 common 的
 * （直读设置源，每次 load 取一次，与独立前"每次 load 重读设置"同语义）。
 */
expect fun defaultPlayerNetworkConfig(): PlayerNetworkConfig

/** mpv 选项快照：按原引擎映射表需要的原始值取，翻译仍在各引擎内。 */
fun defaultPlayerMpvOptions(): PlayerMpvOptions = PlayerMpvOptions(
    profile = SettingsRepository.mpvProfile,
    hwdec = SettingsRepository.mpvHwdec,
    cacheSecs = SettingsRepository.mpvCacheSecs,
    framedrop = SettingsRepository.mpvFramedrop,
    deband = SettingsRepository.mpvDeband,
    networkTimeout = SettingsRepository.mpvNetworkTimeout,
    tlsVerifyDisabled = SettingsRepository.mpvTlsVerify,
    interpolation = SettingsRepository.mpvInterpolation,
    customParams = SettingsRepository.customMpvParams,
    gpuNextRenderer = SettingsRepository.enableGPUNextRenderer,
)

/** 每次调用重新快照（语义同上）。 */
val defaultPlayerMpvOptionsProvider: PlayerMpvOptionsProvider = ::defaultPlayerMpvOptions

/**
 * 网关改写（原 `mediaUrlForGate` / `applyEchGateForLoad` / `EchGateDataSource.open`
 * 三处收敛到此）：返回改写后 URL + 需附加的网关头；**不该用网关时返回 null**。
 *
 * 判定经 [EgressPlanner]：播放链路与 HTTP 链路共用同一份"该不该用网关"
 * （用户开着 / 进程在跑 / 未熔断 / 每条 URL 自己的条件）。此处只看端口的话，
 * 熔断期间媒体仍会被改写——那正是"页面上已经好了、视频还在撞网关"的形态。
 */
internal fun gateRewrite(uri: String): Pair<String, Map<String, String>>? {
    val rewrite = EgressPlanner.gateRewriteFor(uri) ?: return null
    return rewrite.url to mapOf(EchGatePolicy.TARGET_HEADER to rewrite.targetHost)
}
