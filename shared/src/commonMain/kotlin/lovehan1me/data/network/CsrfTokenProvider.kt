package lovehan1me.data.network

/**
 * csrfToken 全局唯一持有者（P4b：AppViewModel 依赖 WorkManager 留在 :app，
 * 但多数 VM 都引用其 csrfToken 全局——拆出本 holder 供 shared 内 VM 使用）。
 * :app 的 [lovehan1me.app.AppViewModel] 委托给本对象，
 * 保证"首页/视频页刷新 csrfToken"与各 VM 读到的是同一份。
 *
 * P0-4：按站隔离。CSRF 是按站签发的，`SiteSwitcher` 切站时全局置 null 会让
 * 回切旧站的写操作（收藏/评分/评论）带错 token → 419。用 host→token 表：
 * 读走"当前站命中、否则全局回退"（老调用方零改动），切站时 stash/restore。
 */
object CsrfTokenProvider : IHCsrfToken {
    override var csrfToken: String? = null

    private val siteTokens = mutableMapOf<String, String?>()

    internal fun normalizeHost(raw: String): String = raw
        .substringAfter("://", raw)
        .substringBefore("/")
        .substringBefore(":")
        .trim()
        .lowercase()

    /** 当前 host 的有效 token：本站命中优先，否则回退全局（兼容老调用方）。 */
    fun tokenFor(host: String): String? {
        val key = normalizeHost(host)
        if (key.isBlank()) return csrfToken
        return if (siteTokens.containsKey(key)) siteTokens[key] else csrfToken
    }

    /** 以某站身份写入：同时更新本站槽与全局（全局即"最近一次有效的那把"）。 */
    fun setTokenFor(host: String, token: String?) {
        val key = normalizeHost(host)
        if (key.isBlank()) {
            csrfToken = token
            return
        }
        siteTokens[key] = token
        csrfToken = token
    }

    /**
     * 切站交接：把手头这把存到旧站槽，再把新站槽的装回来（没有即 null，
     * 由新站页面重新抓取）。与 `SiteSwitcher.switchTo` 配对调用。
     */
    fun stashForSwitch(previousHost: String, nextHost: String) {
        val prev = normalizeHost(previousHost)
        val next = normalizeHost(nextHost)
        if (prev.isNotBlank()) siteTokens[prev] = csrfToken
        csrfToken = if (next.isBlank()) null else siteTokens[next]
    }

    fun clearFor(host: String) {
        val key = normalizeHost(host)
        if (key.isNotBlank()) siteTokens.remove(key)
    }
}

