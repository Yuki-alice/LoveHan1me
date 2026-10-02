package lovehan1me.data.network

import lovehan1me.core.constant.HanimeConstants

/**
 * Swift 侧 Go 网关的**端口回填口**。
 *
 * iOS 的网关运行时是 gomobile 进程内起服（`Echgate.xcframework`），
 * 由壳工程的 `EchGateBootstrap.swift` 在启动时拉起，拿到实际端口后调
 * [setPort] 写进来；Kotlin 侧所有改写层（Ktor 插件等）以此为准。
 * 流量为零时网关空转，无开关也无害；是否真正改写仍看用户设置
 * （见 `installEchGate` 的 `portProvider` 门控）。
 *
 * ## 名字里的分工
 * 本对象**不实现** jvm 侧的 `EchGateStarter` 接口 —— iOS 的起服/生命周期由壳
 * （Swift）自己管，Kotlin 只是被动收端口。故取名"回填口"而不是"起服器"，
 * 免得与那个同包同名（分属不同源集）的接口混淆。
 */
object EchGatePortReporter {

    /** 网关就绪，写入实际监听端口。 */
    fun setPort(port: Int) {
        EchGate.publish(EchGateStatus.Running(port))
    }

    /** 网关停止，恢复未运行态。 */
    fun setStopped() {
        EchGate.publish(EchGateStatus.Stopped)
    }

    /**
     * 起服参数 `ip-list` 的**兜底种子**（静态 CF 边缘 IP，逗号分隔）。
     *
     * iOS 壳把它传给 `gate.StartFlat` 的第二个参数。为什么不能留空：网关自己经 DoH
     * 解析会被污染（2026-10-02 Android 实测 hanime1.me → Facebook 段、拨号 15s 超时），
     * 而 iOS 走 Darwin 引擎、Kotlin 侧没有 [lovehan1me.core.constant.HanimeConstants.CF_FALLBACK_IPS]
     * 那套解析链可以复用，这里就是它唯一的种子来源。网关会逐 IP 拨号挑能连的。
     */
    fun fallbackSeedCsv(): String = HanimeConstants.CF_FALLBACK_IPS.joinToString(",")
}
