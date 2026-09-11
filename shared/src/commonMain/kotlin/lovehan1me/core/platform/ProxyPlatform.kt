package lovehan1me.core.platform

/**
 * 阶段一④：重建系统级代理。
 *
 * 备份/恢复导入设置后需要让代理配置立即生效。
 *
 * - JVM（Android / Desktop）：设置 `proxySet/proxyHost/proxyPort` 系统属性并重建
 *   Ktor 客户端（见 `HProxySelector.rebuildNetwork`）。
 * - iOS：没有系统代理能力，设置页已明示
 *   "Custom DNS / proxy is not available on iOS"，故恒为 no-op。
 *
 * 放在 commonMain 是为了让 `BackupManager` 能整体下沉到公共层——
 * 此前它卡在 jvmMain，唯一原因就是直接依赖了 JVM 侧的 `HProxySelector`。
 */
expect fun rebuildSystemProxy()
