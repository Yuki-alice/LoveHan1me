package lovehan1me.data.network.egress

import io.ktor.http.Url
import lovehan1me.core.constant.HanimeConstants

/**
 * 出站域分类：决定"直连要不要试"。
 *
 * 受限域（[Hanime]/[Getchu]/[CdnMedia]）的直连已知撞 RST，调度器不排 Direct；
 * [ThirdParty]（弹弹play、更新检查等）永不进网关，直连保留。
 */
enum class DomainClass {
    Hanime,
    Getchu,
    CdnMedia,
    ThirdParty,
}

/** getchu 主机。jvmMain `HanimeDns` 里有同名私有常量，调度器在 commonMain 需自备一份。 */
const val GETCHU_HOST = "www.getchu.com"

/**
 * 按 host 优先、用途兜底分类。
 *
 * host 命中 hanime 表（见 [HanimeConstants.HANIME_HOSTNAME]）或 getchu 即定；
 * 媒体用途（Image/Video/Download）下未知 host 是图床/CDN，直连同样不可信；
 * Api 用途下未知 host 才是第三方（自有 client，本就不装网关拦截器）。
 * 解析失败一律 ThirdParty：分类器永远不能成为请求失败的原因。
 */
fun classifyDomain(rawUrl: String, purpose: EgressPurpose): DomainClass {
    val host = runCatching { Url(rawUrl).host.lowercase() }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: return DomainClass.ThirdParty
    if (HanimeConstants.HANIME_HOSTNAME.any { it.equals(host, ignoreCase = true) }) {
        return DomainClass.Hanime
    }
    if (host == GETCHU_HOST) return DomainClass.Getchu
    return when (purpose) {
        EgressPurpose.Image, EgressPurpose.Video, EgressPurpose.Download -> DomainClass.CdnMedia
        EgressPurpose.Api, EgressPurpose.Probe -> DomainClass.ThirdParty
    }
}

/** 该域的直连是否已知不可用（决策第 5 条：受限域跳过 Direct）。 */
val DomainClass.isRestricted: Boolean get() = this != DomainClass.ThirdParty

/**
 * 仅凭 host 的受限判定（F5 代理规则分流的判据）。
 *
 * 代理选择器只有一个 URI，拿不到 [EgressPurpose]，而 [classifyDomain] 对未知 host
 * 必须靠用途才能分流，故这里只认**已知域名**：Hanime 四站 / getchu → 受限，
 * 其余 → 第三方（Rules 模式下直连）。
 *
 * 与 F5 的目标一致：媒体用途下未知 host 在调度器里算 [DomainClass.CdnMedia]（受限），
 * 但让图床走代理只会浪费带宽、拖慢加载 —— 代理分流宁可让它直连。
 */
fun isRestrictedHost(host: String): Boolean {
    if (host.isBlank()) return false
    val h = host.lowercase()
    return h == GETCHU_HOST || HanimeConstants.HANIME_HOSTNAME.any { it.equals(h, ignoreCase = true) }
}
