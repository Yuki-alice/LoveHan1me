package lovehan1me.app.crash

import kotlin.system.exitProcess

// M5-3：桌面无 CrashActivity 机制，改为"落盘 + 下次启动展示"。
actual fun installCrashHandler() {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching { saveCrashReport(buildCrashReport(throwable)) }
        previous?.uncaughtException(thread, throwable)
        // 崩溃后进程已处于不确定状态，直接退出，由用户重启后看到崩溃页
        exitProcess(10)
    }
}
