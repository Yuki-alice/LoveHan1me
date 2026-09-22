package lovehan1me.data.network

/**
 * Swift 侧 Go 网关的回填口。
 *
 * iOS 的网关运行时是 gomobile 进程内起服（`Echgate.xcframework`），
 * 由壳工程的 `EchGateBootstrap.swift` 在启动时拉起，拿到实际端口后调
 * [setPort] 写进来；Kotlin 侧所有改写层（Ktor 插件等）以此为准。
 * 流量为零时网关空转，无开关也无害；是否真正改写仍看用户设置
 * （见 `installEchGate` 的 `portProvider` 门控）。
 */
object EchGateStarter {

    /** 网关就绪，写入实际监听端口。 */
    fun setPort(port: Int) {
        EchGate.port = port
    }

    /** 网关停止，恢复未运行态。 */
    fun setStopped() {
        EchGate.port = -1
    }
}
