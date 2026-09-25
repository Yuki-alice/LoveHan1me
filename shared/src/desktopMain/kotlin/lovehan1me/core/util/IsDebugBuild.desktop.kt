package lovehan1me.core.util

// 桌面：只认显式开关（Gate4-平台能力补齐）。
// JVM 分不清 dev-run 与发行包（同一套 jvmArgs 进 jpackage），故用系统属性：
// `run` 任务（desktopApp/build.gradle.kts）带 `-Dlovehan1me.debug=true`，
// 发行包不带，默认 false（宁可藏起调试入口，不可外泄）。
// 语义与 Android/iOS 的"构建形态"对齐：只管开关，不管来源。
actual fun isDebugBuild(): Boolean =
    System.getProperty("lovehan1me.debug", "false").toBoolean()
