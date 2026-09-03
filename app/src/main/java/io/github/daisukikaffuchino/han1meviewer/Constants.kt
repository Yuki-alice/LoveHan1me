package io.github.daisukikaffuchino.han1meviewer

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.char

// EMPTY_STRING 与 LOCAL_DATE_FORMAT 已下沉 shared commonMain（BasicConstants.kt）

const val APP_NAME = "Han1meViewer"

// 标准时间格式


// 網絡常量已下沉 shared commonMain（NetworkConstants.kt）：USER_AGENT / DESKTOP_USER_AGENT /
// HANIME_BASE_URL / HanimeConstants / HANIME_LOGIN_URL / GETCHU_BASE_URL

// 設置發佈日期年份，在搜索的tag裏

/**
 * 發佈日期年份開始於
 */
const val SEARCH_YEAR_RANGE_START = 1990

/**
 * 發佈日期年份結束於
 */
const val SEARCH_YEAR_RANGE_END = BuildConfig.SEARCH_YEAR_RANGE_END

const val VIDEO_COMMENT_PREFIX = "video"

const val PREVIEW_COMMENT_PREFIX = "preview"

// github url

const val HA1_GITHUB_URL = "https://github.com/daisukiKaffuChino/Han1meViewer"

const val HA1_GITHUB_ISSUE_URL = "$HA1_GITHUB_URL/issues"

const val HA1_GITHUB_FORUM_URL = "$HA1_GITHUB_URL/discussions"
// for Shared Preference

const val LOGIN_COOKIE = "cookie"
const val SAVED_USER_ID = "saved_user_id"

const val CLOUDFLARE_COOKIE = "cf_cookie"
const val CLOUDFLARE_COOKIE_HOST = "cf_cookie_host"

const val ALREADY_LOGIN = "already_login"

// Notification

const val DOWNLOAD_NOTIFICATION_CHANNEL = "download_channel"

const val UPDATE_NOTIFICATION_CHANNEL = "update_channel"

// File

const val FILE_PROVIDER_AUTHORITY = "${BuildConfig.APPLICATION_ID}.fileProvider"
