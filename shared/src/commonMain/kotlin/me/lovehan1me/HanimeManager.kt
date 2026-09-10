package me.lovehan1me

// HANIME_BASE_URL 已下沉 shared（NetworkConstants.kt），同包跨模块需显式 import
import me.lovehan1me.HANIME_BASE_URL
// EMPTY_STRING 已下沉 shared（BasicConstants.kt）
import me.lovehan1me.EMPTY_STRING
import me.lovehan1me.logic.SettingsRepository
import kotlinx.serialization.json.Json

val HJson = Json {
    ignoreUnknownKeys = true
}

/**
 * 给用户显示的错误信息
 *
 * ぴえん化
 */
val Throwable.pienization: CharSequence get() = "🥺\n$message"

// base

/**
 * 獲取 Hanime 影片地址
 */
fun getHanimeVideoLink(videoCode: String) = HANIME_BASE_URL + "watch?v=" + videoCode


/**
 * 獲取 Hanime 搜索地址
 */
fun getHanimeSearchLink(artist: String) = HANIME_BASE_URL + "search?query=" + artist
/**
 * 獲取 Hanime 影片分享文本
 */
fun getHanimeShareText(title: String, videoCode: String): String = buildString {
    appendLine(title)
    appendLine(getHanimeVideoLink(videoCode))
    append("- From Han1meViewer -")
}
/**
 * 獲取 Hanime 影片分享文本
 */
fun getHanimeSearchShareText(artist: String): String = buildString {
    appendLine(artist)
    appendLine(getHanimeSearchLink(artist))
    append("- From Han1meViewer -")
}

/**
 * 獲取 Hanime 影片**官方**下載地址
 */
fun getHanimeVideoDownloadLink(videoCode: String) =
    HANIME_BASE_URL + "download?v=" + videoCode

// videoUrlRegex 与 String.toVideoCode() 已下沉 shared（HanimeUrlRegex.kt）

