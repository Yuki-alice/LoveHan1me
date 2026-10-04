package lovehan1me.core.platform

import lovehan1me.data.network.CdnIpProbe
import lovehan1me.data.network.HanimeProxySelector
import lovehan1me.data.network.egress.RouteRegistry

// 同时服务 androidMain 与 desktopMain：自定义层级里两者同属 jvm 中间源集。
actual fun rebuildSystemProxy() {
    HanimeProxySelector.rebuildNetwork()
    // 出口现实变了（换站 / 换代理 / 备份恢复），上次探测的"可用 IP"不再作数：
    // 开强制档会钉死旧 IP，自动档会复用旧排序。下次解析重新探测，
    // 最坏多一次 ~1.2s 探测，好过拿着死 IP 撞超时。
    CdnIpProbe.invalidate()
    // 同一个理由作用在各域健康上：出口换了，"这个网关还能不能用"的旧结论也失效。
    // 不给它归零的话，用户换到一条能用的代理后，网关照旧被冷却期挡着 —— 那是误判。
    RouteRegistry.reset()
}
