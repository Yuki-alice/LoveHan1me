package lovehan1me.app.crash

// M5-3：Android 保留原有 CrashHandler → CrashActivity 链路（崩溃即刻见页面，
// 体验优于"下次启动才提示"），故此处不重复注册全局 handler。
// `:app` 的 HanimeApplication 负责装配 CrashHandler。
actual fun installCrashHandler() {
}
