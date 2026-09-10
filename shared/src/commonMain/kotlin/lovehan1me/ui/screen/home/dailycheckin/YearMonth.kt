package lovehan1me.ui.screen.home.dailycheckin

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * P6d-2：kotlinx-datetime 无 YearMonth，手写轻量替代（只实现本批次用到的成员）。
 *
 * 成员清单（grep 调用点）：
 * - C8：now() / lengthOfMonth() / atDay() / minusMonths() / plusMonths() / formatYm() / atEndOfMonth()
 * - Content/Report/Calendar：of() / atDay() / lengthOfMonth() / isBefore()
 * - Screen（:app 留存）：now() / of() / plusMonths(Long) / isBefore() / isAfter() / monthsBetween()
 */
data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
    override fun compareTo(other: YearMonth): Int =
        compareValuesBy(this, other, { it.year }, { it.month })
    fun atDay(day: Int): LocalDate = LocalDate(year, month, day)

    fun atEndOfMonth(): LocalDate = atDay(lengthOfMonth())

    fun lengthOfMonth(): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> throw IllegalArgumentException("Invalid month: $month")
    }

    fun plusMonths(months: Int): YearMonth {
        val total = (year * 12 + (month - 1)) + months
        return YearMonth(total / 12, total % 12 + 1)
    }

    fun plusMonths(months: Long): YearMonth = plusMonths(months.toInt())

    fun minusMonths(months: Int): YearMonth = plusMonths(-months)

    fun minusMonths(months: Long): YearMonth = plusMonths(-months.toInt())

    fun isBefore(other: YearMonth): Boolean =
        year < other.year || (year == other.year && month < other.month)

    fun isAfter(other: YearMonth): Boolean =
        year > other.year || (year == other.year && month > other.month)

    /** 原 `month.format(DateTimeFormatter.ofPattern("yyyy-MM"))`（DAO 月前缀查询用）。 */
    fun formatYm(): String = "$year-${month.toString().padStart(2, '0')}"

    companion object {
        fun now(): YearMonth = today().let { YearMonth(it.year, it.monthNumber) }

        fun of(year: Int, month: Int): YearMonth = YearMonth(year, month)

        private fun isLeapYear(year: Int): Boolean =
            (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }
}

/** 原 `LocalDate.now()`（默认时区语义对齐）。 */
fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

/** 原 `ChronoUnit.MONTHS.between(a, b).toInt()`。 */
fun monthsBetween(start: YearMonth, end: YearMonth): Int =
    (end.year - start.year) * 12 + (end.month - start.month)

/** 原 `date.format(DateTimeFormatter.ofPattern("MM月dd日"))`（MM/dd 零填充与原 pattern 一致）。 */
fun LocalDate.formatMd(): String =
    "${monthNumber.toString().padStart(2, '0')}月${dayOfMonth.toString().padStart(2, '0')}日"

/** 原 `date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))`。 */
fun LocalDate.formatYmd(): String =
    "${year}年${monthNumber.toString().padStart(2, '0')}月${dayOfMonth.toString().padStart(2, '0')}日"

/** 原 `time.format(DateTimeFormatter.ofPattern("HH:mm"))`。 */
fun kotlinx.datetime.LocalTime.formatHm(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** 原 `date.format(DateTimeFormatter.ofPattern("MM月dd日 EEEE"))`（中文星期，EEEE ≡ 星期X）。 */
fun LocalDate.formatMdWeek(): String {
    // P6d-2：kotlinx-datetime 0.8.0 的 DayOfWeek 无 isoDayNumber，用 ordinal+1（周一=1..周日=7）。
    val week = when (dayOfWeek.ordinal + 1) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        else -> "日"
    }
    return "${formatMd()} 星期$week"
}

/** 原 `"%04d%02d".format(year, month)`（PreviewUtils 日期码）。 */
fun ymCode(year: Int, month: Int): String =
    "${year.toString().padStart(4, '0')}${month.toString().padStart(2, '0')}"

fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)
