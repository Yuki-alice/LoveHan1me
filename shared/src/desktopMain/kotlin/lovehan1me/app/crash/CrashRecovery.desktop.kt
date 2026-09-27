package lovehan1me.app.crash

import kotlin.system.exitProcess

// M5-3：桌面无 CrashActivity 机制，改为"落盘 + 下次启动展示"。
actual fun installCrashHandler() {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        // 窗口 resize 时 Skiko 可能在 scene teardown 之后多渲染一帧，
        // 抛 RootNodeOwner is already disposed。这只是丢一帧，
        // 不落盘、不转交、不退出，EDT 下一帧继续合成。
        if (isBenignDesktopRenderRace(throwable)) {
            System.err.println("Desktop: ignoring benign render race: $throwable")
            return@setDefaultUncaughtExceptionHandler
        }
        runCatching { saveCrashReport(buildCrashReport(throwable)) }
        previous?.uncaughtException(thread, throwable)
        // 崩溃后进程已处于不确定状态，直接退出，由用户重启后看到崩溃页
        exitProcess(10)
    }
}
