package lovehan1me.core.platform

import lovehan1me.data.network.egress.RouteRegistry

/**
 * 「出口现实变了」的统一复位入口：切站 / 改代理 / 恢复备份 / 换网都走它。
 *
 * ## 两层分工
 * - **本函数（commonMain）**：复位**跨平台**的出口判定状态 —— 各域熔断/择优健康度（[RouteRegistry]）；
 * - **[platformRebuildSystemProxy]（各端 actual）**：复位**平台专有**的传输层状态。
 *
 * ## 为什么熔断复位必须在 common（缺陷 F22）
 * 它此前写在 jvm 的 actual 里，而 iOS 的 actual 是空实现 —— 于是 **iOS 切站不复位熔断健康度**：
 * 旧站的"网关被熔断"结论会带到新站，最长 5 分钟冷却期内网关"开了也不管事"。
 * 这与"判定收在 commonMain、平台只提供原语"这条架构线相悖，本函数是它的修复：
 * 平台无关的复位不再由平台实现负责，新增平台不会漏。
 *
 * 复位理由（原本写在 jvm 那份里，现随代码上移）：
 * 出口换了，"这个网关还能不能用"的旧结论也失效；不给它归零的话，
 * 用户换到一条能用的代理后，网关照旧被冷却期挡着 —— 那是误判。
 */
fun rebuildSystemProxy() {
    RouteRegistry.reset()
    platformRebuildSystemProxy()
}

/**
 * [rebuildSystemProxy] 的**平台专有部分**。
 *
 * - JVM（Android / Desktop）：设置系统代理属性、清 CDN 连通性探测缓存、清 DoH 负缓存冷却；
 * - iOS：没有系统代理能力（设置页已明示 "Custom DNS / proxy is not available on iOS"），
 *   也没有 CDN 探测缓存与 DoH 冷却，恒为 no-op。
 *
 * 声明在 commonMain 是为了让 `BackupManager` 与 `SiteSwitcher` 能整体停在公共层 ——
 * 此前它卡在 jvmMain，唯一原因就是直接依赖了 JVM 侧的 `HanimeProxySelector`。
 */
expect fun platformRebuildSystemProxy()
