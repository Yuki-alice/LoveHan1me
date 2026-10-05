package lovehan1me.data

import lovehan1me.data.SettingsRepository

/**
 * 首页 HTML 的 stale-while-revalidate 缓存（首屏秒开用）。
 *
 * 冷启动首页要等一次 6s+ 的网关请求；把上次成功的 HTML 落盘，
 * 下次启动先解析展示（~0.2s），再正常拉新覆盖 —— 用户看到内容
 * 而不是骨架屏。刷新行为不变（永远拉新）。
 *
 * - key 含域名 + 登录身份：切站/登录态变化自然 miss，不会串台；
 * - 旧 key 文件在写入时顺手清掉，不堆积；
 * - 读到坏文件解析失败就当 miss（调用方 runCatching），下次成功覆盖。
 *
 * 发现页默认浏览（空搜 page=1）复用同一套机制（`discover_*` key）：
 * 每次进 tab 不再白等整轮网络，先展上次第一页再后台刷新。
 * 只缓存"无条件浏览"，key 不含筛选参数 —— 有筛选/有词的请求天然 miss，
 * 不会串结果。
 */
fun homePageCacheKey(): String {
    val domain = runCatching { SettingsRepository.domainName }.getOrDefault("default")
    val user = runCatching { SettingsRepository.savedUserId }.getOrDefault("").ifBlank { "anon" }
    val safeHost = domain.substringAfter("://", domain).substringBefore("/").ifBlank { "default" }
    return "home_${safeHost}_${user}"
}

/** 发现页默认浏览缓存 key（与首页同输入，`discover_` 前缀隔离）。 */
fun discoverCacheKey(): String {
    val domain = runCatching { SettingsRepository.domainName }.getOrDefault("default")
    val user = runCatching { SettingsRepository.savedUserId }.getOrDefault("").ifBlank { "anon" }
    val safeHost = domain.substringAfter("://", domain).substringBefore("/").ifBlank { "default" }
    return "discover_${safeHost}_${user}"
}

/** 读缓存 HTML；null = 无缓存/不可用（调用方直接走网络）。 */
expect fun readCachedHomeHtml(key: String): String?

/** 写缓存 HTML（含旧 key 清理）；失败静默（缓存是加速项，不是正确性项）。 */
expect fun writeCachedHomeHtml(key: String, html: String)

/** 发现页默认浏览读缓存（机制同首页，前缀隔离，调用方 runCatching 当 miss）。 */
expect fun readCachedDiscoverHtml(key: String): String?

/** 发现页默认浏览写缓存（只留当前 key，不碰 `home_*` 文件）。 */
expect fun writeCachedDiscoverHtml(key: String, html: String)
