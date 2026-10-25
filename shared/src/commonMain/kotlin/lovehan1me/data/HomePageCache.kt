package lovehan1me.data

import lovehan1me.data.SettingsRepository

/**
 * 首页/发现页 HTML 的 stale-while-revalidate 缓存（首屏秒开用）。
 *
 * 冷启动首页要等一次 6s+ 的网关请求；把上次成功的 HTML 落盘，
 * 下次启动先解析展示（~0.2s），再正常拉新覆盖 —— 用户看到内容
 * 而不是骨架屏。刷新行为不变（永远拉新）。
 *
 * - key 含域名 + 登录身份：切站/登录态变化自然 miss，不会串台；
 * - 旧 key 文件在写入时按 key 前缀顺手清掉，不堆积；
 * - 读到坏文件解析失败就当 miss（调用方 runCatching），下次成功覆盖。
 *
 * 发现页默认浏览（空搜 page=1）复用同一套机制（`discover_*` key）：
 * 每次进 tab 不再白等整轮网络，先展上次第一页再后台刷新。
 * 只缓存"无条件浏览"，key 不含筛选参数 —— 有筛选/有词的请求天然 miss，
 * 不会串结果。
 *
 * 网络优化 3.1：4 个 expect（home/discover × read/write）收敛为
 * [readCachedHtml] / [writeCachedHtml] 两个 —— 清理规则改为按
 * `key.substringBefore('_')` 前缀（`home_` / `discover_` 自理），
 * 新增页面（如预览页）不再需要复制一对 expect/actual。
 * key 生成（[cacheKeyFor] / [homePageCacheKey] / [discoverCacheKey]）不变。
 */
/**
 * 缓存 key 的纯决策：前缀 + 安全 host + 安全用户。
 *
 * 从 `homePageCacheKey` / `discoverCacheKey` 里抽出来 —— 那两个读全局
 * `SettingsRepository`（未初始化时静默退化），headless 测不了；这里只做
 * 字符串变换，可被确定性断言（同 `mergeSearchPage` 的路子）。
 */
internal fun cacheKeyFor(prefix: String, domain: String, user: String): String {
    val safeHost = domain.substringAfter("://", domain).substringBefore("/").ifBlank { "default" }
    val safeUser = user.ifBlank { "anon" }
    return "${prefix}_${safeHost}_${safeUser}"
}

fun homePageCacheKey(): String {
    val domain = runCatching { SettingsRepository.domainName }.getOrDefault("default")
    val user = runCatching { SettingsRepository.savedUserId }.getOrDefault("").ifBlank { "anon" }
    return cacheKeyFor("home", domain, user)
}

/** 发现页默认浏览缓存 key（与首页同输入，`discover_` 前缀隔离）。 */
fun discoverCacheKey(): String {
    val domain = runCatching { SettingsRepository.domainName }.getOrDefault("default")
    val user = runCatching { SettingsRepository.savedUserId }.getOrDefault("").ifBlank { "anon" }
    return cacheKeyFor("discover", domain, user)
}

/** 读缓存 HTML；null = 无缓存/不可用（调用方直接走网络）。 */
expect fun readCachedHtml(key: String): String?

/**
 * 写缓存 HTML（含同前缀旧 key 清理）；失败静默（缓存是加速项，不是正确性项）。
 * 清理按 key 前缀自理：`home_x` 只清 `home_*`，不碰 `discover_*`。
 */
expect fun writeCachedHtml(key: String, html: String)
