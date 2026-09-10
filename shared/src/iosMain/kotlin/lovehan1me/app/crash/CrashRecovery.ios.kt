package lovehan1me.app.crash

import platform.Foundation.NSUserDefaults

// M5-3：iOS 侧崩溃报告落盘。
// 用 NSUserDefaults 而非文件 API：崩溃瞬间 ObjC 桥接越少越可靠，
// 且 NSUserDefaults 的 Kotlin 映射是稳定 API（无 cinterop 试错成本）。
private const val CRASH_KEY = "han1me_crash_report"

actual fun saveCrashReport(report: String) {
    val defaults = NSUserDefaults.standardUserDefaults
    defaults.setObject(report, forKey = CRASH_KEY)
    defaults.synchronize()
}

actual fun takePendingCrashReport(): String? {
    val defaults = NSUserDefaults.standardUserDefaults
    val report = defaults.stringForKey(CRASH_KEY)?.takeIf { it.isNotBlank() }
    if (report != null) defaults.removeObjectForKey(CRASH_KEY)
    return report
}

actual fun clearCrashReport() {
    NSUserDefaults.standardUserDefaults.removeObjectForKey(CRASH_KEY)
}

// M5-3：iOS 暂未接入崩溃捕获。Kotlin/Native 的未捕获 Throwable 不走
// NSSetUncaughtExceptionHandler（该 API 只覆盖 ObjC 异常），可靠方案需
// 信号级捕获或包装顶层 Compose 渲染，留待 M-后续。
actual fun installCrashHandler() {
}
