package lovehan1me.core.constant

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

/**
 * P4：自 :app Constants.kt 下沉的基础常量（Parser 依赖闭包使用，包名不变）。
 * :app Constants.kt 里其余常量保留。
 */
const val EMPTY_STRING = ""

/* yyyy-MM-dd */
// 注：原 :app 处带 @JvmField（仅 JVM 平台注解，commonMain 不可用），已去掉；Kotlin 调用方不受影响
val LOCAL_DATE_FORMAT = LocalDate.Formats.ISO

/* yyyy-MM-dd HH:mm （P6a：Announcement.getFormattedDate 依赖，随模型下沉）*/
val LOCAL_DATE_TIME_FORMAT = LocalDateTime.Format {
    date(LocalDate.Formats.ISO); char(' ')
    hour(); char(':'); minute()
}

// P6d-4：项目仓库地址（原 :app Constants.kt；About/更新相关 UI 依赖，包名不变调用点零改动）
/** 搜索发布日期年份范围（原 :app Constants；END 原为 BuildConfig 构建年，改运行时当前年，跨年自适应） */
const val SEARCH_YEAR_RANGE_START = 1990

val SEARCH_YEAR_RANGE_END: Int get() = Instant.fromEpochMilliseconds(lovehan1me.core.platform.currentEpochMillis()).toLocalDateTime(TimeZone.currentSystemDefault()).year

// M1：更新通道指向本项目自己的仓库（决策 #6）
const val HA1_GITHUB_URL = "https://github.com/Yuki-alice/LoveHan1me"

// M2：评论类型前缀（自 `:app` Constants.kt 下沉，Preview 评论预取用；包名不变调用点零改动）
const val PREVIEW_COMMENT_PREFIX = "preview"

// M3：视频评论前缀（自 `:app` Constants.kt 下沉，评论 Tab 用；包名不变调用点零改动）
const val VIDEO_COMMENT_PREFIX = "video"
const val HA1_GITHUB_ISSUE_URL = "$HA1_GITHUB_URL/issues"

const val HA1_GITHUB_FORUM_URL = "$HA1_GITHUB_URL/discussions"

/**
 * M1：上游项目地址（GPLv3 诚实归属，完整声明见 NOTICE）。
 * 仅供「关于页 → 上游项目」致谢条目展示；**不作为更新通道**。
 */
const val UPSTREAM_GITHUB_URL = "https://github.com/daisukiKaffuChino/Han1meViewer"
