package lovehan1me.core.platform

import lovehan1me.data.network.CdnIpProbe
import lovehan1me.data.network.HanimeDns
import lovehan1me.data.network.HanimeProxySelector

// 同时服务 androidMain 与 desktopMain：自定义层级里两者同属 jvm 中间源集。
//
// 注意：各域熔断健康度的复位**不在这里** —— 它在 common 的 `rebuildSystemProxy()` 里。
// 此前写在本函数内，导致 iOS（actual 为空实现）切站不复位熔断（缺陷 F22）。
actual fun platformRebuildSystemProxy() {
    HanimeProxySelector.rebuildNetwork()
    // 出口现实变了（换站 / 换代理 / 备份恢复），上次探测的"可用 IP"不再作数：
    // 开强制档会钉死旧 IP，自动档会复用旧排序。下次解析重新探测，
    // 最坏多一次 ~1.2s 探测，好过拿着死 IP 撞超时。
    CdnIpProbe.invalidate()
    // DoH 负缓存（连续失败 3 次即整档摘 30s）同样按"出口现实"失效（缺陷 F23）：
    // 它原本只在换网时清。切站前若 DoH 刚进冷却，切站后新站首屏会跳过 DoH 直接走
    // 系统 DNS —— 而系统 DNS 对这些域名正是被污染的那条路，表现为"切了站还是打不开"。
    HanimeDns.clearDohCooldown()
}
