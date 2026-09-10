package lovehan1me

import lovehan1me.data.SettingsRepository

/**
 * 网络层常量（P3：自 :app Constants.kt 下沉，包名不变）。
 *
 * 只迁移网络层/拦截器/服务用到的常量；BuildConfig 相关、
 * UI / 存储键等仍在 :app Constants.kt 保留。
 */

const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36"
const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"

// base url

val HANIME_BASE_URL: String
    get() = SettingsRepository.baseUrl

/**
 * 如果添加备选网址别忘了确认[String.toVideoCode]的videoUrlRegex
 */
object HanimeConstants {
    val HANIME_HOSTNAME = arrayOf("hanime1.me","hanime1.com","hanimeone.me","javchu.com")
    val HANIME_URL = arrayOf("https://hanime1.me/","https://hanime1.com/","https://hanimeone.me/","https://javchu.com/")
    val ANIME_URL = arrayOf("https://hanime1.me/","https://hanime1.com/","https://hanimeone.me/")
}

val HANIME_LOGIN_URL: String
    get() = HANIME_BASE_URL + "login"

const val GETCHU_BASE_URL = "https://www.getchu.com/"
