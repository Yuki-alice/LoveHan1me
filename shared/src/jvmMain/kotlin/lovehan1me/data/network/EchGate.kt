package lovehan1me.data.network

/**
 * 本地 ECH 网关（`echgate`）的运行状态。
 *
 * 网关是一个**独立进程**，监听 `127.0.0.1:<port>`，把站点域名的流量用 ECH
 * （Encrypted Client Hello）加密 ClientHello 送出去。
 *
 * ## 为什么需要它
 * 直连时 TLS 握手的 SNI 是明文的，DPI 看到 `hanime1.me` 就重置连接。实测
 * （2026-09-22）：TCP 能握手（1.05s），TLS 阶段必被 RST；忽略证书校验也一样
 * ⇒ 不是证书问题。ECH 把真 SNI 塞进加密信封，外层只暴露 Cloudflare 的公共名。
 *
 * ## 谁会启动它
 * 目前只有桌面端（Android 需要 gomobile 打包，iOS 没有可执行文件路径）。
 * 没启动时 [port] 为 -1，[lovehan1me.data.network.interceptor.EchGateInterceptor]
 * 自动放行直连，**行为与接入前完全一致**——这是刻意的设计，网关挂了不该连累正常请求。
 */
object EchGate {

    /**
     * 网关监听端口；`-1` = 未运行。
     *
     * 由桌面端的进程管理者写入。读写都发生在网络线程/启动流程上，
     * 用 `@Volatile` 保证跨线程可见即可，不需要锁。
     */
    @Volatile
    var port: Int = -1
}
