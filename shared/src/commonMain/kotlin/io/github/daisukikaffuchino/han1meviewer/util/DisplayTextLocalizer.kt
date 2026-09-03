package io.github.daisukikaffuchino.han1meviewer.util

import io.github.daisukikaffuchino.utils.LanguageHelper

object DisplayTextLocalizer {

    private val viewsRegex = Regex("^(.+?)(万次|萬次|次)$")
    private val relativeTimeRegex = Regex("^(?:ge)?(.+?)(分钟|分鐘|小时|小時|天|周|週|个月|個月|年)前$")

    fun localizeViews(text: String): String {
        val match = viewsRegex.matchEntire(text.trim()) ?: return text
        val count = match.groupValues[1]
        val unit = match.groupValues[2]

        return when (language()) {
            "zh" -> when (unit) {
                "万次", "萬次" -> "${count}万"
                else -> count
            }

            "en" -> when (unit) {
                "万次", "萬次" -> "${count.toKViews()} views"
                else -> "$count views"
            }

            "ja" -> when (unit) {
                "万次", "萬次" -> "${count}万"
                else -> "${count}回"
            }

            else -> when (unit) {
                "万次", "萬次" -> "${count}萬"
                else -> count
            }
        }
    }

    fun localizeRelativeTime(text: String): String {
        val match = relativeTimeRegex.matchEntire(text.trim()) ?: return text
        val count = match.groupValues[1]
        val unit = match.groupValues[2]

        return when (language()) {
            "zh" -> "$count${unit.toSimplifiedUnit()}前"
            "en" -> "$count ${unit.toEnglishUnit(count)} ago"
            "ja" -> "$count${unit.toJapaneseUnit()}前"
            else -> "$count${unit.toTraditionalUnit()}前"
        }
    }

    private fun language(): String = LanguageHelper.preferredLanguage.language

    private fun String.toSimplifiedUnit(): String = when (this) {
        "分钟", "分鐘" -> "分"
        "小时", "小時" -> "时"
        "天" -> "天"
        "周", "週" -> "周"
        "个月", "個月" -> "月"
        "年" -> "年"
        else -> this
    }

    private fun String.toTraditionalUnit(): String = when (this) {
        "分钟", "分鐘" -> "分"
        "小时", "小時" -> "時"
        "天" -> "天"
        "周", "週" -> "週"
        "个月", "個月" -> "月"
        "年" -> "年"
        else -> this
    }

    private fun String.toJapaneseUnit(): String = when (this) {
        "分钟", "分鐘" -> "分"
        "小时", "小時" -> "時間"
        "天" -> "日"
        "周", "週" -> "週間"
        "个月", "個月" -> "か月"
        "年" -> "年"
        else -> this
    }

    private fun String.toEnglishUnit(count: String): String {
        val singular = count == "1"
        return when (this) {
            "分钟", "分鐘" -> if (singular) "minute" else "minutes"
            "小时", "小時" -> if (singular) "hour" else "hours"
            "天" -> if (singular) "day" else "days"
            "周", "週" -> if (singular) "week" else "weeks"
            "个月", "個月" -> if (singular) "month" else "months"
            "年" -> if (singular) "year" else "years"
            else -> this
        }
    }

    private fun String.toKViews(): String {
        // P6c：java.math.BigDecimal → 纯字符串十进制移位（×10 + 去尾零），语义等价
        return runCatching {
            val sign = if (startsWith("-")) "-" else ""
            val body = if (sign.isNotEmpty()) drop(1) else this
            val dot = body.indexOf('.')
            val intPart = if (dot < 0) body else body.substring(0, dot)
            val frac = if (dot < 0) "" else body.substring(dot + 1)
            val shiftedInt = intPart + (frac.firstOrNull() ?: '0')
            val shiftedFrac = if (frac.length > 1) frac.drop(1) else ""
            val normInt = shiftedInt.trimStart('0').ifEmpty { "0" }
            val normFrac = shiftedFrac.trimEnd('0')
            val num = if (normFrac.isEmpty()) normInt else "$normInt.$normFrac"
            sign + num + "K"
        }.getOrElse { "${'$'}{this}0K" }
    }
}