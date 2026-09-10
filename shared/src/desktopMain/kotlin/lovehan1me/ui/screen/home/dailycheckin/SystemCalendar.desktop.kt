package lovehan1me.ui.screen.home.dailycheckin

import kotlinx.datetime.LocalDate

// M2：桌面系统日历对接留给 P7，当前返回 false（调用方 toast 提示无可用日历）。
actual suspend fun addToSystemCalendar(date: LocalDate): Boolean = false
