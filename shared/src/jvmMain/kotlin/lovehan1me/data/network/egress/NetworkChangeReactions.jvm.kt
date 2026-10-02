package lovehan1me.data.network.egress

import lovehan1me.core.platform.rebuildSystemProxy
import lovehan1me.data.network.HanimeDns
import lovehan1me.data.network.ServiceCreator

/**
 * 网络变化后的 JVM 侧复位（android / desktop 共用；android 侧由 NetworkCallback 触发）。
 *
 * [rebuildSystemProxy] 是既有的"出口现实变了"语义（切站 / 改代理 / 恢复备份都在用）：
 * 代理解析缓存 + CDN 探测缓存 + 熔断健康度。它对熔断的复位与 [onNetworkChanged]
 * 重复一次——幂等、零成本，换来"每端各自的复位集合"不用散落在调用方。
 */
actual fun platformOnNetworkChanged() {
    rebuildSystemProxy()
    // 旧网络上的空闲连接不再作数。OkHttp 的 evictAll 只摘空闲连接（allocationCount == 0），
    // 进行中的请求（含下载）不受影响——这条假设由 NetworkChangeReactionsTest 固定。
    ServiceCreator.evictConnectionPools()
    // DoH 冷却按 URL 记，但成因在网络：换网后立即给 DoH 一次干净的机会。
    HanimeDns.clearDohCooldown()
}