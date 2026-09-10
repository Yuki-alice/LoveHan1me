package lovehan1me.core.util

/**
 * P6d-1-B3：是否为 debuggable 构建（替代 `:app` 的 `BuildConfig.DEBUG`，shared 拿不到 BuildConfig）。
 *
 * - androidMain：读 applicationInfo 的 `FLAG_DEBUGGABLE` 真检查。
 * - desktopMain / iosMain：恒 false（P7 打包元数据完善时再接）。
 */
expect fun isDebugBuild(): Boolean
