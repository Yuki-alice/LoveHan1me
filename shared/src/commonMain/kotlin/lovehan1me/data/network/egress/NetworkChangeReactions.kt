package lovehan1me.data.network.egress

/**
 * 网络现实变化的复位入口（切网 / 断网恢复时由各端监听调用）。
 *
 * ## 为什么需要
 * 出口相关的运行时状态都隐含"上次判定时的网络现实"这一前提：各域熔断与择优、
 * CDN 内置 IP 的探测排序、DoH 的负缓存、OkHttp 连接池里的空闲连接。网络一换，
 * 这些旧结论全部失效——最刺眼的是网关熔断：切到一条能用的网络后它还在冷却期
 * （最长 5 分钟）里被挡着，用户体感是"换了网还是打不开"。
 *
 * ## 三端怎么接
 * - Android：`HanimeApplication` 的 `registerDefaultNetworkCallback`（只认 onAvailable）；
 * - iOS：壳工程 `NWPathMonitor` → [NetworkChangeBridge]（Swift 调 Kotlin）；
 * - 桌面：JVM 没有网络变化事件，暂不接（已知限制，见网络优化方案）。
 *
 * 判定逻辑一律不动：这里只复位状态，下一次请求仍由 [EgressScheduler] 全量重算。
 */
fun onNetworkChanged() {
    // 各域的熔断与择优结论都隐含旧网络：一切网即清零（旧网络上"网关被熔断"不应挡住新网络）。
    // 其余（代理解析缓存 / 探测缓存 / 连接池 / DoH 冷却）只有 JVM 有对应物，
    // 见 [platformOnNetworkChanged]。
    RouteRegistry.reset()
    platformOnNetworkChanged()
}

/**
 * 平台侧的复位动作。JVM：代理解析缓存 + CDN 探测缓存 + 空闲连接摘除 + DoH 冷却；
 * iOS：没有对应物（NSURLSession 连接池由系统管理），no-op。
 */
expect fun platformOnNetworkChanged()