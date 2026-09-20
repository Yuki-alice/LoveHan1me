package lovehan1me.site

/**
 * 站点目录（单站实现的注册表；Gate2-站点纵深扩展时以此为落点）。
 *
 * 现阶段仅作契约落点，不接管现有 [lovehan1me.core.constant.HanimeConstants] / [lovehan1me.core.constant.HANIME_BASE_URL]；
 * Parser 重写时，解析器改为通过 [SiteCatalog] 拿配置，网络层不再直接依赖 SettingsRepository 的 baseUrl 字符串。
 */
object SiteCatalog {
    val Hanime1 = SiteConfig(
        id = SiteId.Hanime1,
        displayName = "Hanime1",
        baseUrls = listOf(
            "https://hanime1.me/",
            "https://hanime1.com/",
            "https://hanimeone.me/",
            "https://javchu.com/",
        ),
        hostnames = listOf(
            "hanime1.me",
            "hanime1.com",
            "hanimeone.me",
            "javchu.com",
        ),
        requiresCloudflare = true,
    )

    val Getchu = SiteConfig(
        id = SiteId.Getchu,
        displayName = "Getchu",
        baseUrls = listOf("https://www.getchu.com/"),
        hostnames = listOf("www.getchu.com"),
        requiresCloudflare = false,
    )

    val all: List<SiteConfig> get() = listOf(Hanime1, Getchu)

    fun byId(id: SiteId): SiteConfig? = all.firstOrNull { it.id == id }
    fun byId(value: String): SiteConfig? = byId(SiteId(value))
}
