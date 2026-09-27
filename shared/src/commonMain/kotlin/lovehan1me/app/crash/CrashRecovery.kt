package lovehan1me.app.crash

/**
 * M5-3：三端崩溃恢复。
 *
 * 原 `:app` 的崩溃处理是 Android 专属链路：`CrashHandler` 捕获未捕获异常 →
 * 启动 `CrashActivity`（新任务）→ 显示 `CrashScreen`。桌面与 iOS 没有
 * "再起一个 Activity" 的能力，因此这里改用移动端/桌面通用的两步式：
 *
 * 1. 崩溃时把报告落盘（[saveCrashReport]），进程照常终止；
 * 2. 下次启动由共享 `App()` 调 [takePendingCrashReport] 检测残留报告，
 *    非空则先渲染 [CrashScreen]，用户复制/重启/退出后清除。
 *
 * Android 保留原有的 CrashActivity 链路（体验更好：崩溃即刻见页面），
 * 故其 [installCrashHandler] 为空实现，由 `:app` 的 Application 装配。
 *
 * UI 见 `ui.screen.crash.CrashScreen`（已下沉 commonMain）。
 */
const val CRASH_PACKAGE_FILTER = "lovehan1me"

/** 注册未捕获异常处理器。应在各端入口尽早调用一次。 */
expect fun installCrashHandler()

/** 取出上次崩溃留下的报告并清除；无残留返回 null。 */
expect fun takePendingCrashReport(): String?

/** 落盘崩溃报告（崩溃瞬间调用，需尽量少依赖、不能抛异常）。 */
expect fun saveCrashReport(report: String)

/** 丢弃残留报告。 */
expect fun clearCrashReport()

fun buildCrashReport(throwable: Throwable): String = buildString {
    appendLine("====== beginning of crash ======")
    appendLine(throwable.stackTraceToString())
}

/**
 * 桌面渲染 teardown 竞态是否为可忽略的一帧。
 *
 * 拉伸窗口时 Skiko 的 onRender 与 scene teardown 可能交错，
 * 抛 `IllegalArgumentException: RootNodeOwner is already disposed`
 *（或关闭弹窗场景的 `IllegalStateException: ComposeScene is closed`）。
 * 这只是当前帧被丢弃，下一帧会正常合成，不代表应用状态已损坏，
 * 崩溃处理器不应为此落盘与退出进程。沿因果链逐层判定，
 * 因为 EDT 上拿到的往往是包了多层 cause 的转包装异常。
 */
fun isBenignDesktopRenderRace(throwable: Throwable): Boolean {
    var current: Throwable? = throwable
    while (current != null) {
        val message = current.message ?: ""
        if (current is IllegalArgumentException &&
            message.contains("RootNodeOwner is already disposed")
        ) {
            return true
        }
        if (current is IllegalStateException &&
            message.contains("ComposeScene is closed")
        ) {
            return true
        }
        current = current.cause
    }
    return false
}
