package lovehan1me.core.platform

import lovehan1me.data.network.HanimeProxySelector

// 同时服务 androidMain 与 desktopMain：自定义层级里两者同属 jvm 中间源集。
actual fun rebuildSystemProxy() {
    HanimeProxySelector.rebuildNetwork()
}
