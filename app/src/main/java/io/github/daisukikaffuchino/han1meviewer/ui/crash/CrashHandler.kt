package io.github.daisukikaffuchino.han1meviewer.ui.crash

import android.content.Context
import android.content.Intent
import android.os.Process
import io.github.daisukikaffuchino.han1meviewer.ui.activity.CrashActivity
import kotlin.system.exitProcess

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val intent = Intent(context, CrashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_LOGS, throwable.stackTraceToString())
        }
        context.startActivity(intent)

        defaultHandler?.uncaughtException(thread, throwable)
        Process.killProcess(Process.myPid())
        exitProcess(10)
    }

    companion object {
        const val EXTRA_LOGS = "logs"
    }
}
