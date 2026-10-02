package lovehan1me.data.network.egress

/**
 * Swift 侧 NWPathMonitor 的回填口（与 `EchGateStarter` 同型）。
 *
 * 壳工程在 path 变化时调用；Kotlin 侧复位出口运行时状态（见 [onNetworkChanged]）。
 */
object NetworkChangeBridge {

    /** 网络路径变化（可用网络切换 / 断网恢复）。 */
    fun onPathChanged() {
        onNetworkChanged()
    }
}