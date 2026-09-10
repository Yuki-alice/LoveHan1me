package lovehan1me.logic.network

import android.content.Context

/**
 * Cloudflare 拦截器与 :app 验证 UI 之间的回调注入点（P3 新增）。
 *
 * CloudflareInterceptor 依赖 WebView 交互（:app 侧的 CloudflareVerificationCoordinator
 * 依赖 MainActivity），无法下沉 shared。因此拦截器只在这里注册回调：
 *
 * ```
 * // :app HanimeApplication.onCreate：
 * CloudflareVerifier.launcher = { ctx, url -> CloudflareVerificationCoordinator.verify(ctx, url) }
 * ```
 */
object CloudflareVerifier {

    @Volatile
    var launcher: (suspend (context: Context, url: String) -> Boolean)? = null

    suspend fun verify(context: Context, url: String): Boolean =
        launcher?.invoke(context, url) ?: false
}
