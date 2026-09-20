package lovehan1me.site

import lovehan1me.core.constant.HanimeConstants
import lovehan1me.data.SettingsRepository

/**
 * 当前数据源的「站点身份」判定（P0 收口）。
 *
 * ---
 *
 * ### 为什么需要它
 *
 * 收口前，全仓库有 6 处按同一表达式判定「当前是不是 AV 站（javchu）」：
 *
 * ```kotlin
 * SettingsRepository.baseUrl == HanimeConstants.HANIME_URL[3]
 * ```
 *
 * 这个表达式有两个静默失效点（都不报错、只退化成番剧站逻辑）：
 *
 * 1. **自定义镜像** —— [SettingsRepository.baseUrl] 在 `useCustomMirrorSite` 打开时返回的是
 *    **镜像地址**，与 `HANIME_URL[3]` 的字面量永不相等；
 * 2. **字面量形状** —— 尾斜杠有无、大小写差异、协议差异都会让字符串比较失败。
 *
 * 热切换让 `baseUrl` 在运行时可变，踩中概率显著上升，因此必须先收口。
 *
 * ### 判据为什么是 `domainName` 而不是 `baseUrl`
 *
 * [SettingsRepository.domainName] 是用户在网域设置里**选中**的站点，不受镜像覆盖影响。
 * 镜像在语义上只是"同站的另一个入口"，不改变站点身份 —— 所以身份判定只认它。
 *
 * ---
 *
 * 与 [SiteCatalog] 的分工：`SiteCatalog` 是**静态契约**（有哪些站、各自 baseUrls/hostnames），
 * 本对象是**运行时判定**（当前是谁）。若把 javchu 提升为独立 `SiteConfig`（Gate2-站点纵深），
 * 本对象是唯一需要跟着改的地方。
 */
object SiteIdentity {

    /**
     * URL 归一化：去首尾空白 → 转小写 → 补尾斜杠。
     *
     * 只做"判定用"的轻量归一化，不解析 scheme/authority（那是
     * [SettingsRepository.parseRootUrl] 的职责）。
     */
    fun normalize(url: String): String {
        val trimmed = url.trim().lowercase()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    /** 第 [index] 个备选网址是否等价于 [candidate]（归一化后比较）。 */
    fun matches(candidate: String, index: Int): Boolean =
        normalize(candidate) == normalize(HanimeConstants.HANIME_URL[index])

    /**
     * 当前站点是否为 AV 站（javchu.com）。
     *
     * 判据是**用户选中的网域**，不是实际请求的 `baseUrl` —— 见类 KDoc。
     */
    val isAvSite: Boolean
        get() = isAvSite(SettingsRepository.domainName)

    /** 指定网域是否等价于 AV 站（归一化后比较）；供测试与显式传值场景使用。 */
    fun isAvSite(domainName: String): Boolean =
        normalize(domainName) == normalize(HanimeConstants.AV_URL)

    /** 指定网域是否等价于"番剧站"（[HanimeConstants.ANIME_URL] 中的任意一个）。 */
    fun isAnimeSite(domainName: String): Boolean =
        HanimeConstants.ANIME_URL.any { normalize(domainName) == normalize(it) }

    /**
     * 从网域里提取**用于展示的 host**（如 `https://javchu.com/` → `javchu.com`）。
     *
     * 只做轻量解析，不引 `Uri`/`URL`（commonMain 里没有统一实现）。取不到 host 时
     * 退回原串去掉协议前缀 —— 宁可显示得难看一点，也不要显示空白。
     */
    fun toHostName(domainName: String): String {
        val withoutScheme = domainName.trim()
            .substringAfter("://", domainName.trim())
        val host = withoutScheme.substringBefore('/')
        return host.substringBefore('?').ifBlank { domainName.trim() }
    }
}
