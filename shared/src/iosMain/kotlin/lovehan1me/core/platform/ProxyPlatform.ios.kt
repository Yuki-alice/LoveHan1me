package lovehan1me.core.platform

// iOS 无系统代理能力（设置页已明示），也没有 CDN 探测缓存与 DoH 冷却，故无平台专有复位项。
//
// ⚠️ 各域熔断健康度的复位**不在这里**：它是跨平台的，已随 F22 上移到 common 的
// `rebuildSystemProxy()`。此处保持空实现是**正确**的——但"空实现"从此不再意味着
// "切站什么都不复位"。
actual fun platformRebuildSystemProxy() {
}
