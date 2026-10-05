@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package lovehan1me.data

import kotlinx.cinterop.ExperimentalForeignApi
import lovehan1me.core.platform.readBytesAtPath
import lovehan1me.core.platform.writeBytesAtPath
import lovehan1me.core.util.LogUtil
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

private const val TAG = "HomePageCache"
private const val MAX_CACHED_HTML_BYTES = 2 * 1024 * 1024L

@OptIn(ExperimentalForeignApi::class)
private fun cacheDir(): String? {
    val base = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String ?: return null
    val dir = "$base/home-pages"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    return dir
}

private fun fileFor(key: String, dir: String): String {
    val safe = key.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(80)
    return "$dir/$safe.html"
}

private fun fileSizeOf(path: String): Long {
    val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, null)
    return (attrs?.get(NSFileSize) as? NSNumber)?.longLongValue() ?: 0L
}

actual fun readCachedHomeHtml(key: String): String? = runCatching {
    val dir = cacheDir() ?: return null
    val path = fileFor(key, dir)
    if (fileSizeOf(path) !in 1..MAX_CACHED_HTML_BYTES) return null
    readBytesAtPath(path)?.decodeToString()
}.onFailure { LogUtil.w(TAG, "read cache failed: ${it.message}") }.getOrNull()

actual fun writeCachedHomeHtml(key: String, html: String) {
    runCatching {
        val dir = cacheDir() ?: return
        val current = fileFor(key, dir)
        // 只留当前 key：切站/换号的旧文件不堆积。
        NSFileManager.defaultManager.contentsOfDirectoryAtPath(dir, error = null)
            ?.filterIsInstance<String>()
            ?.filter { it.startsWith("home_") && "$dir/$it" != current }
            ?.forEach { NSFileManager.defaultManager.removeItemAtPath("$dir/$it", error = null) }
        if (!writeBytesAtPath(current, html.encodeToByteArray())) {
            LogUtil.w(TAG, "write cache failed")
        }
    }.onFailure { LogUtil.w(TAG, "write cache failed: ${it.message}") }
}

actual fun readCachedDiscoverHtml(key: String): String? = runCatching {
    val dir = cacheDir() ?: return null
    val path = fileFor(key, dir)
    if (fileSizeOf(path) !in 1..MAX_CACHED_HTML_BYTES) return null
    readBytesAtPath(path)?.decodeToString()
}.onFailure { LogUtil.w(TAG, "read discover cache failed: ${it.message}") }.getOrNull()

actual fun writeCachedDiscoverHtml(key: String, html: String) {
    runCatching {
        val dir = cacheDir() ?: return
        val current = fileFor(key, dir)
        // 只留当前 key（`discover_` 前缀自理，不碰 `home_*` 文件）。
        NSFileManager.defaultManager.contentsOfDirectoryAtPath(dir, error = null)
            ?.filterIsInstance<String>()
            ?.filter { it.startsWith("discover_") && "$dir/$it" != current }
            ?.forEach { NSFileManager.defaultManager.removeItemAtPath("$dir/$it", error = null) }
        if (!writeBytesAtPath(current, html.encodeToByteArray())) {
            LogUtil.w(TAG, "write discover cache failed")
        }
    }.onFailure { LogUtil.w(TAG, "write discover cache failed: ${it.message}") }
}
