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

// SEARCH_YEAR_RANGE_START/END 已下沉 shared（BasicConstants.kt，END 改运行时当前年）

const val VIDEO_COMMENT_PREFIX = "video"

const val PREVIEW_COMMENT_PREFIX = "preview"

// github url 三常量已下沉 shared commonMain（BasicConstants.kt，P6d-4），包名不变调用点零改动
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
