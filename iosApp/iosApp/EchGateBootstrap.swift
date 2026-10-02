import Foundation
import Echgate
import ComposeApp

/// iOS 本地 ECH 网关的拉起（gomobile 进程内起服）。
///
/// 与桌面的 exe 进程模型对应：监听 127.0.0.1 随机端口，实际端口回填给
/// Kotlin（`EchGatePortReporter.setPort`），Kotlin 侧 Ktor 插件据此改写。
/// 是否真正改写以后端 `useEchGate` 设置为准——这里无条件起服，
/// 无流量时网关空转，用户关掉开关即零改动（见 `installEchGate` 门控）。
enum EchGateBootstrap {
    private static var server: GateServer?

    /// CF 站点表（与 commonMain `HANIME_HOSTNAME` 同源，改一处别忘另一处）。
    private static let cfHosts = "hanime1.me,hanime1.com,hanimeone.me,javchu.com"

    static func startIfNeeded() {
        guard server == nil else { return }
        DispatchQueue.global(qos: .utility).async {
            let cacheDir: String = {
                let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
                let dir = (base?.appendingPathComponent("echgate").path) ?? (NSTemporaryDirectory() as String)
                try? FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)
                return dir
            }()
            var err: NSError?
            guard let srv = GateStartFlat(
                "127.0.0.1:0",
                // ip-list：静态 CF 边缘 IP 种子（Kotlin 侧 HanimeConstants.CF_FALLBACK_IPS）。
                // 不能留空 —— 网关自己 DoH 解析会被污染（实测拿到 Facebook 段假 IP、拨号超时），
                // 而 iOS 没有 HanimeDns 那套解析链可在 Kotlin 侧兜底。网关会逐 IP 拨号挑能连的。
                EchGatePortReporter.shared.fallbackSeedCsv(),
                cfHosts,
                "https://dns.alidns.com/resolve",
                "cloudflare-ech.com",
                "",
                cacheDir,
                15000,
                &err
            ) else {
                NSLog("[EchGate] start failed: \(err?.localizedDescription ?? "unknown")")
                return
            }
            // addr 形如 127.0.0.1:PORT
            let port = srv.addr().split(separator: ":").last.flatMap { Int($0) } ?? -1
            guard port > 0 else {
                NSLog("[EchGate] bad listen addr: \(srv.addr())")
                return
            }
            server = srv
            EchGatePortReporter.shared.setPort(port: Int32(port))
            NSLog("[EchGate] running on 127.0.0.1:\(port)")
        }
    }
}
