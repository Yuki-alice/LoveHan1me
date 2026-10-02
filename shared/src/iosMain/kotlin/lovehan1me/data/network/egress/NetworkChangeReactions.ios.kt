package lovehan1me.data.network.egress

/**
 * iOS 侧没有可复位的传输层状态：连接池由 NSURLSession 管理（Darwin 引擎无接管口），
 * DNS / 探测缓存不存在。熔断器的复位在 common 的 [onNetworkChanged] 里已完成。
 */
actual fun platformOnNetworkChanged() {
}