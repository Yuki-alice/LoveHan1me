package lovehan1me.core.constant

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

    /**
     * AV 站（javchu）网域。
     *
     * 与 [HANIME_URL] / [ANIME_URL] 的分工：后两者是"番剧站"备选表，本常量是**唯一的 AV 站**。
     * 之所以单独取名而不写 `HANIME_URL[3]`：下标会被"往数组里插一个镜像"这类改动静默错位，
     * 而错位后代码不报错、只是把 AV 站当番剧站跑。
     *
     * 站点身份判定请统一走 `lovehan1me.site.SiteIdentity`，不要在业务层直接比字符串 ——
     * 直比 [lovehan1me.data.SettingsRepository.baseUrl] 会被自定义镜像覆盖。
     */
    val AV_URL = HANIME_URL[3]
}

val HANIME_LOGIN_URL: String
    get() = HANIME_BASE_URL + "login"

const val GETCHU_BASE_URL = "https://www.getchu.com/"
