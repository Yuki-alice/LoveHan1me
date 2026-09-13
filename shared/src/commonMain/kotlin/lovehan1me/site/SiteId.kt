package lovehan1me.site

import kotlin.jvm.JvmInline

/**
 * 站点标识（不透明字符串，M6 契约）。
 *
 * 网络层只存 [value]，不解析语义；语义（baseUrl、域名、解析器）全在 [SiteConfig] / [SiteCatalog]。
 * 新增站点时网络层零改动。
 */
@JvmInline
value class SiteId(val value: String) {
    companion object {
        val Hanime1 = SiteId("hanime1")
        val Getchu = SiteId("getchu")
    }
}
