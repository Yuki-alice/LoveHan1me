import Foundation
import Echgate
import ComposeApp

/// iOS 本地 ECH 网关的拉起（gomobile 进程内起服）。
///
/// 与桌面的 exe 进程模型对应：监听 127.0.0.1 随机端口，实际端口回填给
/// Kotlin（`EchGatePortReporter.setPort`），Kotlin 侧 Ktor 插件据此改写。
/// 是否真正改写以后端 `useEchGate` 设置为准——这里无条件起服，
/// 无流量时网关空转，用户关掉开关即零改动（见 `installEchGate` 门控）。
///
/// 起服参数（DoH/超时/ech-b64/上游代理）一律经 Kotlin 桥
/// （`EchGateStartArgs`）取生效值 —— 用户在设置页改的东西对 iOS 网关同样生效
/// （B4-3b；此前全写死在这里，设置页对 iOS 网关无效）。本文件只保留
/// Swift 侧自有的数据：种子 IP（Kotlin 桥 `fallbackSeedCsv`）与 cf-hosts 表。
enum EchGateBootstrap {
    private static var server: GateServer?

    /// CF 站点表（与 commonMain `HANIME_HOSTNAME` 同源，改一处别忘另一处）。
    private static let cfHosts = "hanime1.me,hanime1.com,hanimeone.me,javchu.com"

    static func startIfNeeded() {
        guard server == nil else { return }
        DispatchQueue.global(qos: .utility).async {
            // 网络优化 1.3：先落"拉起中"，冷启动的首个请求才有就绪可等
            // （Kotlin 侧 IosGateReadiness），不再直连抢跑。
            EchGatePortReporter.shared.markStarting()
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
                // B4-3b：DoH/超时/ech-b64 跟随用户设置（Kotlin 桥给生效值）；
                // B2-4：上游代理交给网关出站（空 = 直连模式）。
                EchGateStartArgs.shared.dohUrl(),
                "cloudflare-ech.com",
                EchGateStartArgs.shared.echB64(),
                cacheDir,
                EchGateStartArgs.shared.timeoutMs(),
                EchGateStartArgs.shared.upstreamProxy(),
                &err
            ) else {
                NSLog("[EchGate] start failed: \(err?.localizedDescription ?? "unknown")")
                // 失败也要离开 Starting：否则就绪等待者会等到上限、后续请求白等一轮。
                EchGatePortReporter.shared.setStopped()
                return
            }
            // addr 形如 127.0.0.1:PORT
            let port = srv.addr().split(separator: ":").last.flatMap { Int($0) } ?? -1
            guard port > 0 else {
                NSLog("[EchGate] bad listen addr: \(srv.addr())")
                EchGatePortReporter.shared.setStopped()
                return
            }
            server = srv
            EchGatePortReporter.shared.setPort(port: Int32(port))
            NSLog("[EchGate] running on 127.0.0.1:\(port)")
        }
    }
}
