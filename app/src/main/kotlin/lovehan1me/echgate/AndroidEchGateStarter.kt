package lovehan1me.echgate

import gate.Gate
import gate.Server
import lovehan1me.core.constant.HanimeConstants
import lovehan1me.core.util.LogUtil
import lovehan1me.data.network.EchGate
import lovehan1me.data.network.EchGateRuntime
import lovehan1me.data.network.EchGateStarter
import lovehan1me.data.network.EchGateStatus
import lovehan1me.data.network.echGateSeedIps
import java.io.File

/**
 * Android 侧的网关起服器：gomobile **进程内**起服（`app/libs/Echgate.aar`）。
 *
 * ## 为什么与桌面不同
 * Android 无法执行应用私有目录里的可写文件（API 29+ 的 W^X），所以走不了桌面那条
 * "解包 exe 再 spawn"的路；这里与 iOS 同模型、同一份 `gate.Start`：
 * 网关跑在应用进程里，监听 `127.0.0.1`，App 只做 URL 改写（见 `EchGatePolicy`）。
 *
 * ## 为什么落在 :app 而不是 :shared
 * AAR 是 `:app` 的本地文件依赖（AGP 不允许 library 模块吃本地 `.aar`），
 * 于是引用 `gate.Gate` 的代码只能在应用壳里。这与 iOS 把起服放在壳工程
 * （`EchGateBootstrap.swift`）是同一种分工：**壳负责起服，只有 [EchGate] 与门面对外**。
 *
 * ## 参数与 iOS 对表（ip-list 除外）
 * 监听回环随机端口；**ip-list 传探测过的 CF 种子**（`echGateSeedIps()`，与桌面
 * 同口径）——国内 DoH 对被阻断域名会返回假 IP（模拟器实测 hanime1.me →
 * Facebook 段、拨号超时），不传种子网关就会撞在上面；cf-hosts 取自 common 常量
 * （不再制造第三份站点表拷贝）；DoH 端点 / ECH 域 / 启动超时与 iOS 同值。
 */
internal class AndroidEchGateStarter(
    /** ECH 公钥配置的磁盘缓存目录（应用缓存目录，秒级就绪的关键）。 */
    private val cacheDir: File,
) : EchGateStarter {

    @Volatile
    private var server: Server? = null

    @Synchronized
    override fun start(): Boolean {
        if (server != null) return true

        // 类初始化即加载 libgojni.so。显式 touch 让"产物没打进包"这类错误在这里
        // 就带类名暴露，而不是等到 startFlat 里以 UnsatisfiedLinkError 的形式出现。
        Gate.touch()

        val started = runCatching {
            Gate.startFlat(
                "127.0.0.1:0",
                // ip-list：探测过能建连的 CF 种子（与桌面同口径）——网关自己经 DoH
                // 解析会拿到被污染的假 IP（模拟器实测），种子只多不少地并入。
                echGateSeedIps().joinToString(","),
                // 只有这些域名才配用 CF IP + ECH；其余域名由网关自己按 CNAME / 普通 TLS 走。
                HanimeConstants.HANIME_HOSTNAME.joinToString(","),
                DOH_URL,
                ECH_DOMAIN,
                // ech-b64 留空：让网关自己经 DoH 取 ECH 公钥配置，与 iOS 一致。
                "",
                cacheDir.absolutePath,
                STARTUP_TIMEOUT_MS,
            )
        }.onFailure {
            LogUtil.e(TAG, "网关起服异常", it)
            EchGate.publish(EchGateStatus.Failed("网关启动失败"))
        }.getOrNull() ?: return false

        // addr 形如 127.0.0.1:PORT。
        val port = started.addr().substringAfterLast(':').toIntOrNull() ?: -1
        if (port <= 0) {
            runCatching { started.close() }
            EchGate.publish(EchGateStatus.Failed("网关监听地址异常"))
            LogUtil.w(TAG, "网关监听地址异常：${started.addr()}")
            return false
        }
        // 起服尾段：起服期间用户可能已经关掉开关（首次 DoH 最长 15 秒），
        // 那时门面已落 Stopped —— 这里必须复查，否则会把"开关已关"覆盖成"正在运行"，
        // 留下一个关不掉的网关。判据收在门面里，可离线断言（见 EchGateRuntimeTest）。
        server = started
        if (!EchGateRuntime.publishRunningIfStillStarting(port)) {
            server = null
            runCatching { started.close() }
            LogUtil.i(TAG, "起服期间已被停止，就地回收（${started.addr()}）")
            return false
        }
        // 端口回填即"全部改写层自动生效"的信号：拦截器 / 播放器 / 图片链都看 port > 0。
        LogUtil.i(TAG, "ECH 网关已就绪：${started.addr()}")
        return true
    }

    @Synchronized
    override fun stop() {
        val s = server ?: return
        server = null
        // close() 释放 Go 侧监听与后台 goroutine；幂等。
        runCatching { s.close() }.onFailure { LogUtil.w(TAG, "网关停服异常：${it.message}") }
        LogUtil.i(TAG, "ECH 网关已停止")
    }

    private companion object {
        const val TAG = "EchGate"

        /** DoH 端点 / ECH 域 / 启动超时：与 iOS `EchGateBootstrap.swift` 同值。 */
        const val DOH_URL = "https://dns.alidns.com/resolve"
        const val ECH_DOMAIN = "cloudflare-ech.com"
        const val STARTUP_TIMEOUT_MS = 15000L
    }
}
