package lovehan1me.app.web

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import lovehan1me.core.util.LogUtil
import lovehan1me.data.network.egress.ProxyState
import lovehan1me.data.network.egress.connectTunnelUrl
import lovehan1me.data.network.egress.currentEgressState
import java.util.concurrent.Executor

/**
 * 把验证用 WebView 的出站**对齐到 App 此刻的出口**。
 *
 * ## 为什么必须对齐
 * `cf_clearance` 绑 UA + **出口 IP**（见 `SettingsRepository.clearCloudFlareCookie`）。
 * App 的 HTTP 层经拦截器改写后走"网关 / 用户代理"那条链，而 WebView 默认走**系统代理**
 * —— 两边出口不同，表现就是"**验证过了仍然要验证**"。
 *
 * 桌面端早已这么做（`CloudflareCdp.proxyFlag` 用同一个 `GateState.connectTunnelUrl()`
 * 拼 `--proxy-server=`）；本文件是它在 Android 上的等价物。
 *
 * ## 为什么用 CONNECT 隧道而不是主力通道
 * WebView 没法逐请求塞 `X-Ech-Target` 头，主力通道（网关代为 TLS、能用 ECH）
 * 从浏览器侧走不了。隧道只做 DoH 解析 + 裸 TCP，标准 HTTP 代理语义即可用
 * ——它的价值是**同出口**，不是绕 SNI（见 `echgate/main.go` 的两条通道一节）。
 *
 * ## 这是进程级覆盖
 * `ProxyController.setProxyOverride` 作用于**本进程所有 WebView**，且会顶掉系统代理。
 * 命中范围由本对象收敛：只在验证页加载期间挂上，页面销毁即 [clear]。
 */
internal object GateWebViewProxy {

    private const val TAG = "EchGate"

    /**
     * 当前该给 WebView 挂什么出站，**按门面状态分两支**（与桌面 `CloudflareCdp.proxyFlag` 同口径）：
     *
     * 1. 网关是可用候选（用户开着 + 在跑 + 未熔断）⇒ 指到它的 CONNECT 隧道，
     *    并以 [ProxyConfig.Builder.addDirect] 兜底；
     * 2. 网关不在候选里 ⇒ 用**用户手填的代理**（Http / Socks），让验证窗与
     *    `HanimeProxySelector` 同出口；没配就是 null（不动 WebView，即系统代理语义）。
     *
     * ## 为什么隧道后面接"直连"而不是"用户代理"
     * 网关是候选时，App 的 HTTP 层全部经网关出站 —— 由于代理选择器对回环短路，
     * 这条路的出口是**本机 IP**。于是：
     * - 隧道可用：WebView 也走本机 ⇒ 两边同出口；
     * - 隧道坏掉（网关在跑但这一跳不通）：落到直连仍是本机 IP；
     *   若这时改落用户代理，出口会变成代理 IP —— 与 App 反而不一致，
     *   正是我们要消灭的"验证过了仍然要验证"。
     * 所以"用户代理"属于第 2 支（网关整个不在候选里时），不是隧道的失败兜底。
     *
     * 判据全部取自 [currentEgressState]，与拦截器 / 播放器 / 图片链用的是同一份快照 ——
     * 本文件不重新读设置，也不自己判"网关在不在跑"。
     *
     * @return null = 无需覆盖（既没有可用隧道，也没配代理）。
     */
    internal fun overrideConfig(): ProxyConfig? {
        val state = runCatching { currentEgressState() }.getOrNull() ?: return null
        state.gate.connectTunnelUrl()?.let { tunnel ->
            return ProxyConfig.Builder()
                .addProxyRule(tunnel)
                // 规则优先级递减：网关连不上时落到直连，别把"做验证"这条唯一的自救通道堵死。
                .addDirect()
                .build()
        }
        return when (val proxy = state.proxy) {
            is ProxyState.Explicit -> ProxyConfig.Builder()
                .addProxyRule(
                    if (proxy.socks) "socks://${proxy.host}:${proxy.port}"
                    else "http://${proxy.host}:${proxy.port}",
                )
                .build()
            // SystemResolved：系统代理本来就是 WebView 的默认出口，覆盖它没有增量；
            // None：没配代理，同样不必覆盖。
            else -> null
        }
    }

    /**
     * 装上出站覆盖，生效后回调 [onApplied]。
     *
     * @param executor 回调用的执行器；必须是主线程执行器（回调里要碰 WebView）。
     * @return false = 无需覆盖 / 本机 WebView 不支持代理覆盖 / 覆盖报错 —— 调用方**直接加载**
     *   即可（即接入前的行为）。true = 已在途，等 [onApplied]。
     */
    fun install(executor: Executor, onApplied: () -> Unit): Boolean {
        val config = overrideConfig() ?: return false
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            LogUtil.w(TAG, "WebView 不支持代理覆盖，验证窗沿用系统代理")
            return false
        }
        return runCatching {
            ProxyController.getInstance().setProxyOverride(config, executor) { onApplied() }
            true
        }.getOrElse {
            LogUtil.w(TAG, "WebView 出口覆盖失败：${it.message}")
            false
        }
    }

    /** 卸下覆盖，恢复系统代理语义；幂等，验证页销毁时调用。 */
    fun clear(executor: Executor) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
        runCatching {
            ProxyController.getInstance().clearProxyOverride(executor) {}
        }.onFailure { LogUtil.w(TAG, "WebView 出口覆盖清除失败：${it.message}") }
    }
}
