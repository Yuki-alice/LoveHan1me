package lovehan1me.data.network

import lovehan1me.data.SettingsRepository

/**
 * 网关启动参数的生效值（B4-3a/F15）。
 *
 * 三端（桌面 spawn 参数 / Android、iOS gomobile 入口）都该用同一份判定，
 * 平台只负责把结果传下去 —— 与 `echGateUpstreamProxy()` 的既有约定一致。
 *
 * 全部可选：用户没填就走网关默认值（零行为变化）；填了才透传。
 * 读侧的防御（钳制 / https 过滤）是针对脏备份与手改 DataStore 的，
 * 正常路径的格式校验在设置页保存时做（toast 拒绝）。
 */

/** 上游超时秒默认值（= 网关 `--timeout` 默认值，改它等于改跨进程协议的默认值）。 */
const val ECH_GATE_TIMEOUT_SEC_DEFAULT = 15

/** 上游超时秒的用户可调范围（拨号 + 取数的整单预算；太小会误杀慢流）。 */
const val ECH_GATE_TIMEOUT_SEC_MIN = 5
const val ECH_GATE_TIMEOUT_SEC_MAX = 120

/** 生效的上游超时秒：坏数据钳回范围，设置未就绪回默认（判定永远不能成为请求失败的原因）。 */
fun echGateTimeoutSec(): Int =
    runCatching { SettingsRepository.echGateTimeoutSec }
        .getOrNull()
        ?.coerceIn(ECH_GATE_TIMEOUT_SEC_MIN, ECH_GATE_TIMEOUT_SEC_MAX)
        ?: ECH_GATE_TIMEOUT_SEC_DEFAULT

/**
 * 生效的网关 DoH 端点（null = 用网关默认）。
 *
 * 只认 `https://`：明文 DoH 会把"取 ECH 配置"这件事本身暴露出去，
 * 比用默认值更糟 —— 脏数据直接丢弃，而不是将就着用。
 */
fun echGateDohUrl(): String? =
    runCatching { SettingsRepository.echGateDohUrl }
        .getOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it.startsWith("https://") }

/** 生效的手填 ECH 配置（null/空 = 网关经 DoH 自取；解不开时网关侧静默回退）。 */
fun echGateEchB64(): String? =
    runCatching { SettingsRepository.echGateEchB64 }
        .getOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/**
 * 手填 ECH 配置的保存前校验（标准 base64 字母表，Go 侧 `base64.StdEncoding` 同口径）。
 *
 * 只验字母表不验 padding/长度：网关解不开会静默回退 DoH，不会炸；
 * 这里拦的是"明显手误"（贴了 URL、多了中文标点之类）。
 */
fun isPlausibleEchB64(raw: String): Boolean {
    val s = raw.trim()
    return s.isNotEmpty() && s.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '/' || it == '=' }
}
