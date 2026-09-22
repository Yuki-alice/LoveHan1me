package lovehan1me.data.network

import lovehan1me.data.SettingsRepository
import okhttp3.internal.proxy.NullProxySelector
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * 受 [EhViewer_CN_SXJ 中 EhProxySelector](https://github.com/xiaojieonly/Ehviewer_CN_SXJ/blob/BiLi_PC_Gamer/app/src/main/java/com/hippo/ehviewer/EhProxySelector.java)
 * 的启发，本项目的 [HanimeProxySelector] 也采用同样的思路实现代理功能。
 *
 * 在 ECH 网关架构里它是"兜底"：网关优先（[EchGatePolicy] 把流量改写到回环，
 * 这里对回环恒返回直连，避免绕出去再绕回来）；网关失败/未运行时，
 * 这里的 Direct/System/Http/Socks 判定即全部出口语义。
 *
 * @project LoveHan1me
 * @author Yenaly Liew（上游原作者，见 NOTICE）
 * @time 2023/10/07 007 17:32
 */
// #issue-15: 添加系统代理功能
class HanimeProxySelector : ProxySelector() {

    private var delegation: ProxySelector? = null

    /**
     * System 模式的委托目标（构造时抓的 JVM 默认选择器）。
     *
     * 构造时跳过栈里已有的 [HanimeProxySelector]：设置页每次改代理都会重建
     * client（含新的选择器实例），若直接抓 `getDefault()`，Android 全局默认
     * 本身就是旧实例——委托链随设置次数越长越长，语义还冻结在启动时。
     */
    private val alternative: ProxySelector = run {
        var current = getDefault()
        var guard = 0
        while (current is HanimeProxySelector && guard++ < MAX_UNWRAP_DEPTH) {
            current = current.capturedAlternative
        }
        current
    } ?: NullProxySelector

    /** [alternative] 的内联暴露，仅供同类解链用。 */
    internal val capturedAlternative: ProxySelector get() = alternative

    /** [updateProxy] 绑定时的代理类型；设置变更后在 [select] 里惰性重绑。 */
    @Volatile
    private var boundType: Int = Int.MIN_VALUE

    init {
        updateProxy()
    }

    companion object {
        // P6d-4：常量本体上移 commonMain（HProxyTypes），此处转发保持调用点零改动
        const val TYPE_DIRECT = HProxyTypes.TYPE_DIRECT
        const val TYPE_SYSTEM = HProxyTypes.TYPE_SYSTEM
        const val TYPE_HTTP = HProxyTypes.TYPE_HTTP
        const val TYPE_SOCKS = HProxyTypes.TYPE_SOCKS

        /** 解链保护：全局默认正常情况下最多嵌一层 Hanime 实例。 */
        private const val MAX_UNWRAP_DEPTH = 8

        /**
         * 回环永不代理（HttpURLConnection/CDP 轮询/网关本地转发共用）。
         * 与 [select] 的判定同源，抽出来供系统属性（nonProxyHosts）对齐。
         */
        const val NON_PROXY_HOSTS = "localhost|127.*|[::1]"

        private val ipv4Regex =
            Regex("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$")

        fun validateIp(ip: String): Boolean {
            return ipv4Regex.matches(ip)
        }

        fun validatePort(port: Int): Boolean {
            return port in 0..65535
        }

        /**
         * #issue-39: 代理沒有應用到 WebView 上，只能通過此種方式來全局代理。
         *
         * 写两套键：
         * - 标准键（`http(s).proxyHost/Port`、`socksProxyHost/Port`、
         *   `http.nonProxyHosts`）：`DefaultProxySelector` 与 `HttpURLConnection`
         *   真正读的（此前只写 `proxySet/proxyHost/proxyPort`，JVM 根本不认，
         *   所谓"全局代理"实际未生效）；
         * -  legacy 键（`proxySet/proxyHost/proxyPort`）：保留，历史语义兼容。
         *
         * 回环恒进 `nonProxyHosts`：ECH 网关与 CDP 调试端口都在本机，
         * 开着 HTTP 代理时不能把它们也送往外部代理。
         */
        fun rebuildNetwork() {
            val properties = System.getProperties()
            // legacy 键（兼容）
            when (SettingsRepository.proxyType) {
                TYPE_HTTP, TYPE_SOCKS -> {
                    properties["proxySet"] = true.toString()
                    properties["proxyHost"] = SettingsRepository.proxyIp
                    properties["proxyPort"] = SettingsRepository.proxyPort.toString()
                }

                else -> {
                    properties["proxySet"] = false.toString()
                    properties["proxyHost"] = ""
                    properties["proxyPort"] = ""
                }
            }
            // 标准键（真正生效）
            properties["http.nonProxyHosts"] = NON_PROXY_HOSTS
            properties["https.nonProxyHosts"] = NON_PROXY_HOSTS
            when (SettingsRepository.proxyType) {
                TYPE_HTTP -> {
                    properties["http.proxyHost"] = SettingsRepository.proxyIp
                    properties["http.proxyPort"] = SettingsRepository.proxyPort.toString()
                    properties["https.proxyHost"] = SettingsRepository.proxyIp
                    properties["https.proxyPort"] = SettingsRepository.proxyPort.toString()
                    properties.remove("socksProxyHost")
                    properties.remove("socksProxyPort")
                }

                TYPE_SOCKS -> {
                    properties["socksProxyHost"] = SettingsRepository.proxyIp
                    properties["socksProxyPort"] = SettingsRepository.proxyPort.toString()
                    properties.remove("http.proxyHost")
                    properties.remove("http.proxyPort")
                    properties.remove("https.proxyHost")
                    properties.remove("https.proxyPort")
                }

                else -> {
                    properties.remove("http.proxyHost")
                    properties.remove("http.proxyPort")
                    properties.remove("https.proxyHost")
                    properties.remove("https.proxyPort")
                    properties.remove("socksProxyHost")
                    properties.remove("socksProxyPort")
                }
            }
        }
    }

    private fun updateProxy() {
        val type = SettingsRepository.proxyType
        boundType = type
        delegation = when (type) {
            TYPE_DIRECT -> NullProxySelector
            TYPE_SYSTEM -> alternative
            TYPE_HTTP, TYPE_SOCKS -> null
            else -> NullProxySelector
        }
    }

    override fun select(uri: URI?): MutableList<Proxy> {
        // 本地 ECH 网关就在本机：再交给外部代理，等于把流量绕出去又绕回来，
        // 而且绕出去的那一段是明文 SNI——正是我们要躲的东西。
        // 注意只 bypass 回环：IP 字面量在用户手填代理模式下仍要走代理
        // （EchGatePolicy 的宽判定只用于"要不要进网关"，不用于"要不要走用户代理"）。
        val host = uri?.host
        if (host == "127.0.0.1" || host == "localhost" || host == "[::1]" || host == "::1") {
            return mutableListOf(Proxy.NO_PROXY)
        }

        val type = runCatching { SettingsRepository.proxyType }.getOrDefault(TYPE_SYSTEM)
        // 设置在实例构造后被修改（全局默认实例常驻）：惰性重绑，
        // 否则委托冻结在启动时的模式。
        if (type != boundType) updateProxy()
        if (type == TYPE_HTTP || type == TYPE_SOCKS) {
            explicitProxy(type)?.let { return mutableListOf(it) }
        }

        return runCatching {
            delegation?.select(uri) ?: alternative.select(uri)
        }.getOrDefault(mutableListOf(Proxy.NO_PROXY))
    }

    /**
     * 手填代理；null = 配置不完整（没填 IP/端口非法），调用方回退委托。
     * `select()` 按契约不应抛异常——解析失败同样回退，绝不把请求打断在这里。
     */
    private fun explicitProxy(type: Int): Proxy? {
        val ip = runCatching { SettingsRepository.proxyIp }.getOrNull() ?: return null
        val port = runCatching { SettingsRepository.proxyPort }.getOrNull() ?: return null
        if (ip.isBlank() || port !in 1..65535) return null
        // InetSocketAddress(String, int) 内部做惰性解析，不阻塞、不抛；
        // createUnresolved 进一步保证 select() 内零 DNS。
        val socketAddress = java.net.InetSocketAddress.createUnresolved(ip, port)
        return Proxy(
            if (type == TYPE_HTTP) Proxy.Type.HTTP else Proxy.Type.SOCKS,
            socketAddress
        )
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        // 失败回执转交给实际提供代理的那一级（此前调 `select()` 又丢弃返回值，
        // 等于没通知，黑名单/重试逻辑永远触发不了）。
        runCatching { delegation?.connectFailed(uri, sa, ioe) }
        runCatching { alternative.connectFailed(uri, sa, ioe) }
    }
}
