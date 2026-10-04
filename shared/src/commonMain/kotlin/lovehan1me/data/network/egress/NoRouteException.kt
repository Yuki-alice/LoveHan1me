package lovehan1me.data.network.egress

/**
 * 诚实失败：调度表为空（网关不可用、无代理、直连被跳过）时执行器抛它，**不转圈**。
 *
 * 必须继承 `okio.IOException` 而不是普通 `Exception`：
 * OkHttp 只把 `IOException` 递送给 `Callback.onFailure`（Coil 图片走 `enqueue`），
 * 非 IO 异常会从 `RealCall$AsyncCall` 的裸分发线程逃逸成未捕获异常 → 崩溃退出
 * （2026-10-04 桌面端实测：取消风暴误熔断 CdnMedia 后首个图片请求即 exit 10）。
 * okio 在 JVM 上的 actual 正是 `java.io.IOException` 的 typealias，零新依赖。
 * 上层（`NetworkRepo.handleException`，Phase 2 接入）把它翻成一句人话 +
 * 设置深链（配代理 / 检查网关开关 / 走验证），而不是"请求失败，请重试"。
 */
class NoRouteException(
    val domain: DomainClass,
    val reason: String,
) : okio.IOException("无可用出口（$domain）：$reason")
