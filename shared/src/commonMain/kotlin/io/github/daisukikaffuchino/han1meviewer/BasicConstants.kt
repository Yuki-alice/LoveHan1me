package io.github.daisukikaffuchino.han1meviewer

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.char

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
