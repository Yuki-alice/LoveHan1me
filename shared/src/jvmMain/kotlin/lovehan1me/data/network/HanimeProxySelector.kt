package lovehan1me.data.network

import lovehan1me.data.SettingsRepository
import okhttp3.internal.proxy.NullProxySelector
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * 受 [EhViewer_CN_SXJ 中 EhProxySelector](https://github.com/xiaojieonly/Ehviewer_CN_SXJ/blob/BiLi_PC_Gamer/app/src/main/java/com/hippo/ehviewer/EhProxySelector.java)
 * 的启发，本项目的 [HanimeProxySelector] 也采用同样的思路实现代理功能。
 *
 * @project LoveHan1me
 * @author Yenaly Liew（上游原作者，见 NOTICE）
 * @time 2023/10/07 007 17:32
 */
// #issue-15: 添加系统代理功能
class HanimeProxySelector : ProxySelector() {

    private var delegation: ProxySelector? = null
    private val alternative: ProxySelector = getDefault() ?: NullProxySelector

    init {
        updateProxy()
    }

    companion object {
        // P6d-4：常量本体上移 commonMain（HProxyTypes），此处转发保持调用点零改动
        const val TYPE_DIRECT = HProxyTypes.TYPE_DIRECT
        const val TYPE_SYSTEM = HProxyTypes.TYPE_SYSTEM
        const val TYPE_HTTP = HProxyTypes.TYPE_HTTP
        const val TYPE_SOCKS = HProxyTypes.TYPE_SOCKS

        private val ipv4Regex =
            Regex("^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$")

        fun validateIp(ip: String): Boolean {
            return ipv4Regex.matches(ip)
        }

        fun validatePort(port: Int): Boolean {
            return port in 0..65535
        }

        // #issue-39: 代理沒有應用到 WebView 上，只能通過此種方式來全局代理。
        fun rebuildNetwork() {
            val properties = System.getProperties()
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
        }
    }

    private fun updateProxy() {
        delegation = when (SettingsRepository.proxyType) {
            TYPE_DIRECT -> NullProxySelector
            TYPE_SYSTEM -> alternative
            TYPE_HTTP, TYPE_SOCKS -> null
            else -> NullProxySelector
        }
    }

    override fun select(uri: URI?): MutableList<Proxy> {
        // 本地 ECH 网关就在本机：再交给外部代理，等于把流量绕出去又绕回来，
        // 而且绕出去的那一段是明文 SNI——正是我们要躲的东西。
        val host = uri?.host
        if (host == "127.0.0.1" || host == "localhost") {
            return mutableListOf(Proxy.NO_PROXY)
        }

        val type = SettingsRepository.proxyType
        if (type == TYPE_HTTP || type == TYPE_SOCKS) {
            val ip = SettingsRepository.proxyIp
            val port = SettingsRepository.proxyPort
            if (ip.isNotBlank() && port != -1) {
                val inetAddress = InetAddress.getByName(ip)
                val socketAddress = InetSocketAddress(inetAddress, port)
                return mutableListOf(
                    Proxy(
                        if (type == TYPE_HTTP) Proxy.Type.HTTP else Proxy.Type.SOCKS,
                        socketAddress
                    )
                )
            }
        }

        return delegation?.select(uri) ?: alternative.select(uri)
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        delegation?.select(uri)
    }
}