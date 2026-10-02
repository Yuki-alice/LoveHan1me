package lovehan1me.data.network

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
}
