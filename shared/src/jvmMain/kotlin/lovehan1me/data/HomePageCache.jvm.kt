package lovehan1me.data

import java.io.File
import lovehan1me.core.util.LogUtil
import lovehan1me.data.network.httpCacheDirectory

private const val TAG = "HomePageCache"
private const val MAX_CACHED_HTML_BYTES = 2 * 1024 * 1024

private fun cacheDir(): File =
    File(httpCacheDirectory(), "home-pages").also { if (!it.exists()) it.mkdirs() }

private fun fileFor(key: String): File {
    val safe = key.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(80)
    return File(cacheDir(), "$safe.html")
}

actual fun readCachedHomeHtml(key: String): String? = runCatching {
    fileFor(key).takeIf { it.isFile && it.length() in 1..MAX_CACHED_HTML_BYTES }?.readText()
}.onFailure { LogUtil.w(TAG, "read cache failed: ${it.message}") }.getOrNull()

actual fun writeCachedHomeHtml(key: String, html: String) {
    runCatching {
        val current = fileFor(key)
        // 只留当前 key：切站/换号的旧文件不堆积。
        cacheDir().listFiles { file -> file.isFile && file.name.startsWith("home_") && file != current }
            ?.forEach { it.delete() }
        current.writeText(html)
    }.onFailure { LogUtil.w(TAG, "write cache failed: ${it.message}") }
}

actual fun readCachedDiscoverHtml(key: String): String? = runCatching {
    fileFor(key).takeIf { it.isFile && it.length() in 1..MAX_CACHED_HTML_BYTES }?.readText()
}.onFailure { LogUtil.w(TAG, "read discover cache failed: ${it.message}") }.getOrNull()

actual fun writeCachedDiscoverHtml(key: String, html: String) {
    runCatching {
        val current = fileFor(key)
        // 只留当前 key（`discover_` 前缀自理，不碰 `home_*` 文件）。
        cacheDir().listFiles { file -> file.isFile && file.name.startsWith("discover_") && file != current }
            ?.forEach { it.delete() }
        current.writeText(html)
    }.onFailure { LogUtil.w(TAG, "write discover cache failed: ${it.message}") }
}
