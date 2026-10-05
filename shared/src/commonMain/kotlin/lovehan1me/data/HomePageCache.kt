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
 */
fun homePageCacheKey(): String {
    val domain = runCatching { SettingsRepository.domainName }.getOrDefault("default")
    val user = runCatching { SettingsRepository.savedUserId }.getOrDefault("").ifBlank { "anon" }
    val safeHost = domain.substringAfter("://", domain).substringBefore("/").ifBlank { "default" }
    return "home_${safeHost}_${user}"
}

/** 读缓存 HTML；null = 无缓存/不可用（调用方直接走网络）。 */
expect fun readCachedHomeHtml(key: String): String?

/** 写缓存 HTML（含旧 key 清理）；失败静默（缓存是加速项，不是正确性项）。 */
expect fun writeCachedHomeHtml(key: String, html: String)
