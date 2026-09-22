package lovehan1me.data.network

import kotlin.concurrent.Volatile

/**
 * 本地 ECH 网关（`echgate`）的运行状态。
 *
 * 网关是一个**独立进程/运行时**，监听 `127.0.0.1:<port>`，把站点域名的流量用 ECH
 * （Encrypted Client Hello）加密 ClientHello 送出去。
 *
 * ## 为什么需要它
 * 直连时 TLS 握手的 SNI 是明文的，DPI 看到 `hanime1.me` 就重置连接。实测
 * （2026-09-22）：TCP 能握手（1.05s），TLS 阶段必被 RST；忽略证书校验也一样
 * ⇒ 不是证书问题。ECH 把真 SNI 塞进加密信封，外层只暴露 Cloudflare 的公共名。
 *
 * ## 平台运行时
 * - 桌面：`EchGateProcess` 拉起各 OS 的 Go 二进制（`echgate/build.sh` 产物）；
 * - Android：长期形态是 gomobile 进程内起服（`echgate/gate` 包已就绪），
 *   播放器/下载/图片的改写 plumbing 已全部按 `port > 0` 生效；
 * - iOS：同上，Ktor 插件 + AVPlayer 改写已就绪，运行时待接入。
 *
 * 没启动时 [port] 为 -1，所有改写层自动放行直连，
 * **行为与接入前完全一致**——这是刻意的设计，网关挂了不该连累正常请求。
 * 此时现有机制（代理选择器 / 内置 hosts / DoH）即兜底。
 */
object EchGate {

    /**
     * 网关监听端口；`-1` = 未运行。
     *
     * 由各平台的进程管理者写入。读写都发生在网络线程/启动流程上，
     * 用 `@Volatile` 保证跨线程可见即可，不需要锁。
     */
    @Volatile
    var port: Int = -1
}

/**
 * 按设置确保网关在运行（热切换/备份恢复后调用）。
 *
 * - JVM：`useEchGate` 开着就 `EchGateProcess.start()`（已在运行则 no-op；
 *   进程意外死亡后借此复活）；
 * - iOS：无运行时，no-op。
 */
expect fun ensureEchGateway()
