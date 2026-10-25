package lovehan1me.core.platform

import lovehan1me.data.network.HanimeNetwork

// 各域熔断健康度的复位**不在这里**：它是跨平台的，已随 F22 上移到 common 的
// `rebuildSystemProxy()`。
//
// 网络优化 1.2：本函数从 no-op 改为重建 iOS 传输层 —— 应用内代理走 NSURLSession
// 会话配置，配置在客户端创建时拷贝，改代理 / 备份导入（恢复网络配置）后必须
// 换新客户端才生效。iOS 没有系统代理属性可设，也没有 CDN 探测缓存与 DoH 冷却，
// 这就是它的全部平台专有复位项。
actual fun platformRebuildSystemProxy() {
    HanimeNetwork.rebuildTransports()
}
