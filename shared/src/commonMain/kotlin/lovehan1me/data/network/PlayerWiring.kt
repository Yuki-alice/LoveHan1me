package lovehan1me.data.network

import lovehan1me.core.util.LogUtil
import lovehan1me.data.SettingsRepository
import lovehan1me.data.network.egress.AttemptOutcome
import lovehan1me.data.network.egress.EgressPurpose
import lovehan1me.data.network.egress.EgressReporter
import lovehan1me.data.network.egress.EgressRequest
import lovehan1me.data.network.egress.EgressScheduler
import lovehan1me.data.network.egress.RouteId
import lovehan1me.data.network.egress.RouteRegistry
import lovehan1me.data.network.egress.classifyDomain
import lovehan1me.data.network.egress.currentEgressState
import lovehan1me.data.network.egress.currentForceMode
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
 * 判定经 [EgressScheduler]：播放链路与 HTTP 链路共用同一份计划（含该域熔断、
 * 粘滞优选与强制模式）。此处只看端口的话，熔断期间媒体仍会被改写——那正是
 * "页面上已经好了、视频还在撞网关"的形态。
 *
 * 引擎侧 precedence（见 mpv `openMedia` / Exo `EchGateDataSource.open`）：
 * 改写优先、代理其次 —— 本函数非 null ⟺ 计划首位是 Gate，与
 * 各端 `proxyUrlFor` 的"Gate 首位返回 null"互补，不存在两边同时有值。
 */
internal fun gateRewrite(uri: String): Pair<String, Map<String, String>>? {
    val domain = classifyDomain(uri, EgressPurpose.Video)
    val plan = EgressScheduler.plan(
        EgressRequest(uri, "GET", EgressPurpose.Video, currentForceMode()),
        runCatching { currentEgressState() }.getOrNull() ?: return null,
        RouteRegistry.healthOf(domain),
    )
    val rewrite = plan.attempts.firstOrNull()
        ?.takeIf { it.route == RouteId.Gate }
        ?.rewrite ?: return null
    return rewrite.url to mapOf(EchGatePolicy.TARGET_HEADER to rewrite.targetHost)
}

/**
 * 播放网关链路结局 → [EgressReporter]（F9 / 阶段 4.2）。
 *
 * 只上报 [RouteId.Gate]：调用点（引擎的 `onGateLoadOutcome`）保证只在**确实走了
 * [gateRewrite]** 时回调。视频 CDN 与图床常不同域（如
 * `vdownload.hembed.com → *.rsc.cdn77.org`），此前没有任何数据流进它的 [RouteRegistry]
 * 健康，该域的网关出口**永不熔断**——正是"能浏览、不能播"的账本缺失。
 *
 * 失败按 [AttemptOutcome.TransportError]（攒够阈值才熔）记；不区分网关 502 与连接类
 * 异常：引擎侧（Exo/mpv）拿到的都是 IOException，编一个更细的口径不如诚实粗一点。
 * rtt 记 -1（无样本，不动 EWMA）：一次"加载"跨多个请求，不是一个 RTT。
 */
internal fun reportGateLoadOutcome(uri: String, ok: Boolean, reason: String?) {
    val domain = classifyDomain(uri, EgressPurpose.Video)
    EgressReporter.report(
        domain = domain,
        route = RouteId.Gate,
        outcome = if (ok) AttemptOutcome.Success else AttemptOutcome.TransportError,
        rttMs = -1L,
    )
    if (!ok) LogUtil.w("PlayerWiring", "视频域网关加载失败（$domain）：${reason ?: "未知"}")
}
