package lovehan1me.ui.screen.home.dailycheckin

import kotlinx.datetime.LocalDate

/**
 * M2：把打卡"添加到系统日历"做成平台抽象（原 `:app` `DailyCheckInUtils.createCalendarEvent`）。
 *
 * - androidMain：原 Intent 照搬（ACTION_INSERT + CalendarContract），经
 *   `Han1meDatabaseContext.appContext` 启动（调用点已无 Activity/Context）；
 *   无日历应用时返回 false，调用方 toast 提示。
 * - desktopMain / iosMain：返回 false（P7 再接系统日历）。
 */
expect suspend fun addToSystemCalendar(date: LocalDate): Boolean
