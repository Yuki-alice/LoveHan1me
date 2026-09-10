package io.github.daisukikaffuchino.han1meviewer.logic.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * CF 人机验证挑战请求（跨平台触发总线）。
 *
 * 背景：此前只有 Android 经 OkHttp 拦截器→`CloudflareVerifier`→Activity 跳转
 * 打开验证页；桌面（KCEF 弹窗）/iOS（WKWebView 直嵌）的槽位实现虽已就绪
 * （M5-5），但没有任何调用方把 `CloudflareRoute` 压栈，验证 UI 实际不可达——
 * 桌面/iOS 在 CF 挑战下登录与浏览直接去世（仅一个 toast）。
 *
 * 本总线是三端统一触发点：[NetworkRepo.throwRequestException] 判定
 * 403 + "Just a moment" 时发送（不改变原抛错语义）；共享 `App()` 收集后压栈
 * `CloudflareRoute`，各端既有槽位 UI 原样复用。Android 拦截器链路不受影响：
 * 拦截器消费首次 403（WebView 单独走），只有其重试仍 403 到达此处，
 * 且收集侧以"栈上已有 CF 页"去重，不会双开。
 */
data class CloudflareChallenge(
    val url: String,
    val host: String,
)

object CloudflareChallenges {

    // replay=1： composition 前已发出的挑战不丢失（首页请求可能早于收集器注册）；
    // 已消费的事件不会重放给现有收集器，关闭后的 CF 页不会幽灵重开。
    private val _requests = MutableSharedFlow<CloudflareChallenge>(replay = 1, extraBufferCapacity = 1)
    val requests: SharedFlow<CloudflareChallenge> = _requests.asSharedFlow()

    fun request(url: String) {
        val host = url.substringAfter("://", "")
            .substringBefore("/")
            .substringBefore(":")
            .lowercase()
        if (host.isBlank()) return
        _requests.tryEmit(CloudflareChallenge(url, host))
    }
}
