import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    /// C1b：视频全屏态（Kotlin 侧 `IosFullscreenBridge` 经通知推送）。
    /// 状态栏 + Home 指示条 + 方向三件套，布局态由 Compose 侧 `isFullscreen` 承担。
    @State private var fullscreen = false

    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all) // Compose 自身拥有全部布局空间
            .statusBarHidden(fullscreen)
            .hideHomeIndicator(fullscreen)
            .onReceive(
                NotificationCenter.default.publisher(
                    for: Notification.Name("lovehan1me.fullscreen.changed")
                )
            ) { note in
                guard let v = note.userInfo?["fullscreen"] as? Bool else { return }
                fullscreen = v
                setOrientation(
                    fullscreen: v,
                    forceLandscape: note.userInfo?["forceLandscape"] as? Bool ?? true,
                )
            }
    }

    /// 进入全屏（且要求横屏）→ 转横屏；退出 → 回竖屏；竖屏视频的全屏不转方向。
    /// iOS 16+ 走正规 `requestGeometryUpdate`，失败/低版本回退 UIDevice 旧招
    /// （同 `reference/animeko` 的 `ContentView.setDeviceOrientation` 形状，
    /// 但不用它那个已 deprecated 的 `attemptRotationToDeviceOrientation`）。
    private func setOrientation(fullscreen: Bool, forceLandscape: Bool) {
        let landscape = fullscreen && forceLandscape
        if #available(iOS 16.0, *) {
            if let scene = UIApplication.shared.connectedScenes.first(where: {
                $0.activationState == .foregroundActive
            }) as? UIWindowScene {
                let prefs = UIWindowScene.GeometryPreferences.iOS()
                prefs.interfaceOrientations = landscape ? .landscape : .portrait
                scene.requestGeometryUpdate(prefs) { _ in
                    UIDevice.current.setValue(
                        landscape
                            ? UIDeviceOrientation.landscapeLeft.rawValue
                            : UIDeviceOrientation.portrait.rawValue,
                        forKey: "orientation"
                    )
                }
                return
            }
        }
        UIDevice.current.setValue(
            landscape
                ? UIDeviceOrientation.landscapeLeft.rawValue
                : UIDeviceOrientation.portrait.rawValue,
            forKey: "orientation"
        )
    }
}

extension View {
    /// Home 指示条隐藏（iOS 16+ 才有 API，低版本原样返回）。
    @ViewBuilder
    fileprivate func hideHomeIndicator(_ hidden: Bool) -> some View {
        if #available(iOS 16.0, *), hidden {
            self.persistentSystemOverlays(.hidden)
        } else {
            self
        }
    }
}
