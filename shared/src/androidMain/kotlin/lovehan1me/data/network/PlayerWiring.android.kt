package lovehan1me.data.network

import lovehan1me.core.constant.USER_AGENT
import lovehan1me.data.SettingsRepository
import lovehan1me.feature.player.PlayerNetworkConfig

/**
 * Android 侧网络配置（Gate3-P1）：代理判定沿用原 `MpvPlaybackEngine.settingsHttpProxy`
 *（HTTP 代理才填，`proxyPort in 1..65535`），ECH 启用时回 null（回环不可代理）。
 */
actual fun defaultPlayerNetworkConfig(): PlayerNetworkConfig = object : PlayerNetworkConfig {
    override val userAgent: String = USER_AGENT

    override fun proxyUrlFor(mediaUri: String): String? {
        if (EchGate.port > 0) return null
        val ip = SettingsRepository.proxyIp
            .takeIf { it.isNotBlank() && SettingsRepository.proxyPort in 1..65535 }
            ?: return null
        if (SettingsRepository.proxyType != HanimeProxySelector.TYPE_HTTP) return null
        return "http://$ip:${SettingsRepository.proxyPort}"
    }

    override fun rewriteForGate(uri: String): Pair<String, Map<String, String>>? =
        gateRewrite(uri)
}
