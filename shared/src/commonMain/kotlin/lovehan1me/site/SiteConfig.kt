package lovehan1me.site

/**
 * 站点配置（M6 契约，纯数据，不含 OkHttp/Ktor 实现）。
 *
 * 单站实现（hanime1/getchu）通过 [SiteCatalog] 提供实例，解析层只认 [SiteId]。
 */
data class SiteConfig(
    val id: SiteId,
    val displayName: String,
    /** 站点所有可用 baseUrl（镜像），首个为默认 */
    val baseUrls: List<String>,
    /** 对应的 hostname（与 baseUrls 一一对应，用于 DNS/Host 校验） */
    val hostnames: List<String>,
    /** 是否需要 Cloudflare 验证 */
    val requiresCloudflare: Boolean = true,
) {
    val primaryBaseUrl: String get() = baseUrls.first()
    val primaryHostname: String get() = hostnames.first()
}
