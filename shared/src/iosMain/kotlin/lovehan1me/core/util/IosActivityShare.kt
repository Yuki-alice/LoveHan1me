@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package lovehan1me.core.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene
import kotlin.coroutines.resume

/**
 * C3b：iOS 系统分享面板（`UIActivityViewController`）的唯一唤起口。
 *
 * 文本分享与媒体分享共用：前者传文本，后者传文件 URL。
 * 两个必守的坑：
 * 1. iPad 不设弹窗锚点会崩溃，而锚点 API（`popoverPresentationController`
 *    category 成员）在本版 KN 绑定里解析不到 —— iPad 不进面板，
 *    调用方降级（文本进剪贴板、媒体落 SavedOnly），样式让步、保证不崩。
 *    后续若要 iPad 原生 popover，得走 Swift 侧呈现（Swift 没有绑定缺口），另立项。
 * 2. 只 resume 一次：present 失败与 completion 回调可能叠加，`completed` 旗守住，
 *    协程取消时顺手把 panel 关掉（否则窗悬空）。
 *
 * @return true = 用户点了分享项（completed）；false = 取消/唤起失败/拿不到锚点/iPad。
 *   调用方按 `MediaExportOutcome` 语义翻译：false 且文件已落盘 = SavedOnly。
 */
internal suspend fun presentActivitySheet(items: List<Any>): Boolean =
    withContext(Dispatchers.Main) {
        // 见上：iPad 无安全呈现路径，直接拒掉，调用方按 SavedOnly/复制处理。
        if (isPad()) return@withContext false
        suspendCancellableCoroutine { continuation ->
            var finished = false
            fun finish(value: Boolean) {
                if (!finished) {
                    finished = true
                    continuation.resume(value)
                }
            }
            val anchor = keyRootViewController()
            if (anchor == null) {
                finish(false)
                return@suspendCancellableCoroutine
            }
            val controller = runCatching {
                UIActivityViewController(activityItems = items, applicationActivities = null)
            }.getOrNull() ?: run {
                finish(false)
                return@suspendCancellableCoroutine
            }
            controller.completionWithItemsHandler = { _, completed, _, _ ->
                finish(completed)
            }
            continuation.invokeOnCancellation {
                runCatching {
                    controller.dismissViewControllerAnimated(flag = true, completion = null)
                }
            }
            runCatching {
                anchor.presentViewController(controller, animated = true, completion = null)
            }.onFailure {
                finish(false)
            }
        }
    }

/**
 * 是否跑在 iPad 上（型号字符串判定）。
 *
 * 不用 `UIUserInterfaceIdiom` 枚举：它在本版 KN 绑定里解析不到（与
 * `popoverPresentationController` 同一类缺口）；`model` 是 `UIDevice`
 * 主头属性，"iPad"/"iPhone" 字面十年稳定。
 */
internal fun isPad(): Boolean = runCatching {
    UIDevice.currentDevice.model.contains("iPad", ignoreCase = true)
}.getOrDefault(false)

/** 前台 keyWindow 的 rootViewController；拿不到返回 null（调用方降级，不抛）。 */
private fun keyRootViewController(): UIViewController? {
    for (scene in UIApplication.sharedApplication.connectedScenes) {
        val root = (scene as? UIWindowScene)?.keyWindow?.rootViewController
        if (root != null) return root
    }
    return null
}
