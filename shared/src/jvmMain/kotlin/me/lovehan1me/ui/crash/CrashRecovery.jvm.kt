package me.lovehan1me.ui.crash

import java.io.File

// M5-3：JVM 侧（android+desktop 共用）的崩溃报告落盘。
// 用临时目录文件而非 DataStore：崩溃瞬间协程/异步写入不可靠。
private fun crashFile(): File = File(
    System.getProperty("java.io.tmpdir"),
    "han1me_crash_report.txt",
)

actual fun saveCrashReport(report: String) {
    runCatching { crashFile().writeText(report) }
}

actual fun takePendingCrashReport(): String? {
    val file = crashFile()
    return runCatching {
        file.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
            .also { file.delete() }
    }.getOrNull()
}

actual fun clearCrashReport() {
    runCatching { crashFile().delete() }
}
