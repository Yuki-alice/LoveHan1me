package lovehan1me.core.util

// iOS：Kotlin/Native 编译期即知的二进制形态（Gate4-平台能力补齐）。
// debug 包（Xcode Run / test.kexe）为 true，Release/TestFlight/App Store 为 false。
// 语义与 Android 的 ApplicationInfo.FLAG_DEBUGGABLE 对齐：只管"构建形态"，不管来源。
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
actual fun isDebugBuild(): Boolean = kotlin.native.Platform.isDebugBinary
