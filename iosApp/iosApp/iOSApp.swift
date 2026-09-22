import SwiftUI

@main
struct iOSApp: App {
    init() {
        // 本地 ECH 网关（免梯直连）：后台起服，端口回填 Kotlin；
        // 是否改写以后端设置为准，无流量时空转。
        EchGateBootstrap.startIfNeeded()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
