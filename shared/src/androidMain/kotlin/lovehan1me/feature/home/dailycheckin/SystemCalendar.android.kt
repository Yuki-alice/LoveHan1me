package lovehan1me.feature.home.dailycheckin

import android.content.Intent
import android.provider.CalendarContract
import lovehan1me.Res
import lovehan1me.calendar_desc
import lovehan1me.calendar_location
import lovehan1me.calendar_title
import lovehan1me.data.database.dao.Han1meDatabaseContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.getString

// M2：原 `:app` DailyCheckInUtils.createCalendarEvent 照搬；
// Context 改经 Han1meDatabaseContext.appContext（调用点已无 Activity），
// 启动加 NEW_TASK（非 Activity 上下文必需）。
actual suspend fun addToSystemCalendar(date: LocalDate): Boolean {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        setDataAndType(CalendarContract.Events.CONTENT_URI, "vnd.android.cursor.dir/event")
        putExtra(
            CalendarContract.Events.TITLE,
            getString(Res.string.calendar_title, date.month.ordinal + 1, date.day)
        )
        putExtra(CalendarContract.Events.DESCRIPTION, getString(Res.string.calendar_desc))
        putExtra(
            CalendarContract.Events.EVENT_LOCATION,
            getString(Res.string.calendar_location)
        )
        putExtra(
            CalendarContract.EXTRA_EVENT_BEGIN_TIME,
            date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        )
        putExtra(
            CalendarContract.EXTRA_EVENT_END_TIME,
            date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(TimeZone.currentSystemDefault())
                .toEpochMilliseconds()
        )
        putExtra(CalendarContract.Events.ALL_DAY, true)
        putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        Han1meDatabaseContext.appContext.startActivity(intent)
        true
    } catch (_: android.content.ActivityNotFoundException) {
        false
    }
}
