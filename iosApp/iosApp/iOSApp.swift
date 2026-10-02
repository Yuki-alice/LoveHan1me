import SwiftUI
import Network
import ComposeApp

@main
struct iOSApp: App {
    /// 网络变化监听（切网 / 断网恢复）。
    ///
    /// NWPathMonitor 必须在整个 App 生命周期内被强引用：挂在 static 上而不是
    /// init 的局部变量里，局部变量出栈即被释放、监听取不到任何回调。
    private static let pathMonitor: NWPathMonitor = {
        let monitor = NWPathMonitor()
        monitor.pathUpdateHandler = { _ in
            // 复位 Kotlin 侧的出口运行时状态（熔断器等）：否则网关熔断的冷却期
            // 会跨网络延续（最长 5 分钟），表现为"换了网还是打不开"。
            NetworkChangeBridge.shared.onPathChanged()
        }
        return monitor
    }()

    init() {
        // 本地 ECH 网关（免梯直连）：后台起服，端口回填 Kotlin；
        // 是否改写以后端设置为准，无流量时空转。
        EchGateBootstrap.startIfNeeded()
        iOSApp.pathMonitor.start(queue: DispatchQueue(label: "lovehan1me.network-change"))
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
