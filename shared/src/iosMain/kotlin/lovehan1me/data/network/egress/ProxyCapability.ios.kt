package lovehan1me.data.network.egress

/**
 * iOS 侧恒 false。
 *
 * Darwin 引擎与 AVPlayer 都交系统网络栈出站，应用**拿不到**"当前有没有系统代理"这个事实
 * （设置页也据此明示 "Custom DNS / proxy is not available on iOS"）。
 *
 * 因此 iOS 上"有可用代理"永远为 false ⇒ 网关不进入试用期（没有可以让位的对象），
 * 但熔断照常生效：网关被阻断时仍然会被摘掉，只是退到的是直连而不是代理。
 */
actual fun platformSystemProxyUsable(): Boolean = false
