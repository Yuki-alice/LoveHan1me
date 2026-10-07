package lovehan1me.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import lovehan1me.core.util.PlatformLock
import lovehan1me.core.util.withLock
import lovehan1me.data.network.installEchGate

// iOS 真实现（Gate4-平台能力补齐）：Darwin 引擎 + 通用图片管线（ECH 改写，见帮助函数）。
// 预览模式走默认构造（与 jvm 侧同分支）。
//
// A1：**进程级单例化**（与 jvm 侧同构）。此前 8 个调用点各自 `remember { … }`，
// iOS 侧代价更重 —— 除了各自一份 Coil 缓存，还每次新建一个 `HttpClient(Darwin)`。
// 现在两者都随单例只建一次。

/** 进程级单例的互斥锁，见 jvm 侧同名说明（iOS actual 是真 `NSLock`）。 */
private val loaderLock = PlatformLock()

/** 进程级共享实例。只在 [loaderLock] 内读写。 */
private var singletonLoader: ImageLoader? = null

/**
 * 取进程级单例；**首次调用时构造**（连带 `HttpClient(Darwin)` 也只建一次）。
 *
 * @param inspection 预览 / Inspection 模式。此模式下返回 null —— 预览各自需要自己的
 *   context，不得进进程级缓存（与 jvm 侧同一决策，逐字对应）。
 * @return 进程级共享实例；[inspection] 为 true 时恒为 null。
 */
internal fun hanimeImageLoaderOrNull(
    context: PlatformContext,
    inspection: Boolean,
): ImageLoader? {
    if (inspection) return null
    return loaderLock.withLock {
        singletonLoader ?: ImageLoader.Builder(context)
            .components {
                val client = HttpClient(Darwin) { installEchGate(withCookies = false) }
                add(KtorNetworkFetcherFactory(httpClient = client))
            }
            .build()
            .also { singletonLoader = it }
    }
}

/** 复位进程级单例。**仅供测试**（与 jvm 侧同签名，便于两端共享同一套断言）。 */
internal fun resetHanimeImageLoaderForTest() {
    loaderLock.withLock { singletonLoader = null }
}

@Composable
actual fun rememberHanimeImageLoader(): ImageLoader {
    val context = LocalPlatformContext.current
    val isInspectionMode = LocalInspectionMode.current
    return remember(context, isInspectionMode) {
        // 预览分支返回 null，这里按各自的 context 新建实例（见 hanimeImageLoaderOrNull）。
        hanimeImageLoaderOrNull(context, inspection = isInspectionMode)
            ?: ImageLoader.Builder(context).build()
    }
}
