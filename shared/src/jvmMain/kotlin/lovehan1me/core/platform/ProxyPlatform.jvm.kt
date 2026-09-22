package lovehan1me.core.platform

import lovehan1me.data.network.CdnIpProbe
import lovehan1me.data.network.HanimeProxySelector

// 同时服务 androidMain 与 desktopMain：自定义层级里两者同属 jvm 中间源集。
actual fun rebuildSystemProxy() {
    HanimeProxySelector.rebuildNetwork()
    // 出口现实变了（换站 / 换代理），上次探测的"可用 IP"不再作数：
    // 开强制档会钉死旧 IP，自动档会复用旧排序。下次解析重新探测，
    // 最坏多一次 ~1.2s 探测，好过拿着死 IP 撞超时。
    CdnIpProbe.invalidate()
}
