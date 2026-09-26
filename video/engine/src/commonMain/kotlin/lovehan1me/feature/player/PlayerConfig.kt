package lovehan1me.feature.player

// 播放引擎的网络配置（Gate3-P1 设置项解耦）。
// 引擎此前直读 SettingsRepository / EchGate 单例，导致内核无法独立成模块。
// 改为构造注入：:video:engine 只认本接口，实现由 :shared 的 data 层提供。
interface PlayerNetworkConfig {
    // 播放器对外 UA（Exo 的 HttpDataSource / mpv 的 user-agent 共用）。
    val userAgent: String

    // 本次媒体可用的 HTTP 代理 URL（null = 直连）。
    // ECH 网关启用时必须返回 null：媒体 URL 已被改写到本地回环，
    // ffmpeg 的 http_proxy 没有 bypass 概念，会把回环请求也送走。
    fun proxyUrlFor(mediaUri: String = ""): String?

    // ECH 网关改写：返回改写后 URL + 需附加的网关头，网关未运行返回 null。
    // 对应原 EchGatePolicy.rewrite + mediaUrlForGate 语义。
    fun rewriteForGate(uri: String): Pair<String, Map<String, String>>?
}

// mpv 选项快照（Gate3-P1 设置项解耦）。
// 字段是设置层的原始值（非 mpv 属性值），两端引擎各自按原映射表翻译。
// 由 PlayerMpvOptionsProvider 每次 load 时实时取，用户改完设置下个视频即生效。
data class PlayerMpvOptions(
    val profile: String = "default",
    val hwdec: String = "auto",
    val cacheSecs: Int = 10,
    val framedrop: Boolean = false,
    val deband: Boolean = false,
    val networkTimeout: Int = 30,
    // 注意字段名与 UI 文案相反（历史遗留，勿修正）：
    // true = UI 上的「忽略 HTTPS 证书验证」= mpv 的 tls-verify=no。
    val tlsVerifyDisabled: Boolean = false,
    val interpolation: Boolean = false,
    val customParams: String = "",
    val gpuNextRenderer: Boolean = false,
)

typealias PlayerMpvOptionsProvider = () -> PlayerMpvOptions
