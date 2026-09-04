package io.github.daisukikaffuchino.han1meviewer.ui.screen.home.dailycheckin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.provider.CalendarContract
import android.view.View
import android.view.WindowInsetsController
import io.github.daisukikaffuchino.han1meviewer.R
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.no_calendar_app
import io.github.daisukikaffuchino.han1meviewer.calendar_desc
import io.github.daisukikaffuchino.han1meviewer.calendar_location
import io.github.daisukikaffuchino.han1meviewer.calendar_title
import io.github.daisukikaffuchino.utils.SonnerToast
import io.github.daisukikaffuchino.utils.toastText
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.DateTimeUnit

/**
 * 创建日历事件，用于向系统日历添加未来打卡提醒。
 *
 * P6d-2：date 改 kotlinx（调用方 DailyCheckInScreen 已切 shared 类型）。
 * P6d-3-C2：转 suspend + CMP getString（调用方 scope.launch）。
 *
 * @param context Android Context
 * @param date 提醒日期
 */
suspend fun createCalendarEvent(context: Context, date: LocalDate) {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        setDataAndType(CalendarContract.Events.CONTENT_URI, "vnd.android.cursor.dir/event")
        putExtra(
            CalendarContract.Events.TITLE,
            getString(Res.string.calendar_title, date.monthNumber, date.dayOfMonth)
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
    }
    try {
        context.startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        SonnerToast.warning(getString(Res.string.no_calendar_app))
    }
}

/**
 * 根据是否全屏切换 Activity 的屏幕方向与系统栏可见性。
 *
 * @param isFullscreen 是否进入全屏模式
 */
fun Activity.updateReportWindowMode(isFullscreen: Boolean) {
    requestedOrientation = if (isFullscreen) {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.apply {
            if (isFullscreen) {
                hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                show(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
            }
        }
    } else {
        @Suppress("DEPRECATION")
        run {
            window.decorView.systemUiVisibility = if (isFullscreen) {
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_FULLSCREEN
            } else {
                View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }
}
