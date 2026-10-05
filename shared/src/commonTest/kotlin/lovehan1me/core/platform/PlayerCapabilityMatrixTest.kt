package lovehan1me.core.platform

import lovehan1me.app.bridge.NoopVideoPageHost
import lovehan1me.data.network.defaultPlayerNetworkConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 各平台**钉死的**播放能力真值，由 test 源集 actual 提供（`desktopTest` / `iosTest` /
 * `androidHostTest`）。平台能力变动时，这里必须同改 —— 改一边不改另一边，测试转红。
 */
expect val expectedPlayerCapabilities: PlayerPlatformCapabilities

/**
 * 平台能力矩阵守卫（阶段 4.3）。
 *
 * 目的：防「假动作」——声称支持却空转。守卫分三层：
 * 1. [playerPlatformCapabilities] 与各端钉死的期望表一致（宿主与网络配置的静态答案都由它派生，
 *    故本条同时守住 `supportsFullscreen` / `supportsBrightness` 的真实取值）；
 * 2. 兜底 [NoopVideoPageHost] 不声称任何窗口能力（接口默认值方向必须一致，全 `false`）；
 * 3. 播放网络配置的 UA 非空（iOS 曾发空 UA）。
 *
 * ## 反向验证
 * - 把 `VideoPageHost.supportsFullscreen()` 默认改回 `true` → `兜底宿主...` 转红；
 * - 把任一端 `playerPlatformCapabilities()` 的某项翻转 → `平台能力表...` 转红；
 * - 把某端 UA 改成空串 → `播放网络配置...` 转红。
 *
 * 注：`gateRewrite` / `mediaProxy` 是「该端是否有真实实现」的**声明**（iOS 的 `rewriteForGate`
 * 是恒 null 存根，运行时无法与「网关未运行返回 null」区分），故只能如此钉；声明与实现在宿主层
 * 已由能力表统一，守卫保证声明不被误改。
 */
class PlayerCapabilityMatrixTest {

    @Test
    fun `平台能力表与期望一致`() {
        assertEquals(
            expectedPlayerCapabilities,
            playerPlatformCapabilities(),
            "能力真值与期望表不符：改平台能力必须同改期望表，否则就是假动作回归",
        )
    }

    @Test
    fun `兜底宿主不声称任何窗口能力`() {
        assertFalse(
            NoopVideoPageHost.supportsFullscreen(),
            "默认必须是否——没实现就不许声称支持（否则兜底宿主会让 UI 亮出按了没反应的全屏键）",
        )
        assertFalse(NoopVideoPageHost.supportsBrightness())
        assertFalse(NoopVideoPageHost.shouldEnterPip())
    }

    @Test
    fun `播放网络配置带非空 UA`() {
        assertTrue(
            defaultPlayerNetworkConfig().userAgent.isNotBlank(),
            "空 UA 会让 CDN 收到无标识请求（iOS 曾如此）",
        )
    }

    @Test
    fun `画中画能力与设置页能力表同口径`() {
        assertEquals(
            settingsPlatformCapabilities().pipMode,
            playerPlatformCapabilities().pipMode,
            "两张平台能力表对同一事实必须同口径",
        )
    }
}