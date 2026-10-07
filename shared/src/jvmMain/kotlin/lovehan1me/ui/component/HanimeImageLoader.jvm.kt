package lovehan1me.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import lovehan1me.core.util.PlatformLock
import lovehan1me.core.util.StartupTrace
import lovehan1me.core.util.withLock
import lovehan1me.data.network.createCdnFetchClient

// P6d-2：jvmMain 真实现。构造照抄 getchu 版（OkHttp + HanimeDns + 代理选择器），
// 只是去掉 getchu 域名特化头（通用加载器）；同时覆盖 Android 和桌面。
// 图片同样在 CDN 上（`vdownload.hembed.com/image/…`），和视频一样被 SNI 阻断，
// 故同样常驻 EchGateInterceptor（网关未运行时放行，行为与接入前一致）。
//
// A1：**进程级单例化**。此前这里用 `remember(context, isInspectionMode) { … }` 构造
// ImageLoader，而 remember 的作用域是**调用点** —— 8 个调用点各持有一个实例，每个实例
// 自带独立的 Coil 内存/磁盘缓存，于是同一张封面在不同屏幕/单元格各解码一份、缓存互不命中。
// 现在真正的实例收成进程级一份；remember 保留，只用来记住"本次 context 对应的结果"。
// （OkHttp 那一层本来就是共享的：createCdnFetchClient 内部有 CDN_CLIENTS 缓存，未动。）
//
// A1.5：**桌面注册口也合流到同一份**。桌面 `Main.kt` 的 `setSingletonImageLoaderFactory`
// 原先自建 `ImageLoader.Builder + ktor3 取图器`，与这里的实例不是同一个 —— 单例名义上有、
// 桌面实则岔成两套缓存；且出口错用了带站点 Cookie 的 `createHanimeHttpClient`（会把
// `hanime1_session` 发去图床 host）。现改成委派本文件的 `sharedHanimeImageLoader`。

/**
 * 进程级单例的互斥锁。
 *
 * 用项目既有的跨平台 [PlatformLock]（commonMain expect，JVM=Any、iOS=NSLock），
 * 而不是 `synchronized` / `lazy`：前者在 Native 上不存在，后者无法复位。
 */
private val loaderLock = PlatformLock()

/** 进程级共享实例。只在 [loaderLock] 内读写。 */
private var singletonLoader: ImageLoader? = null

/**
 * 取进程级单例；**首次调用时构造**。非 @Composable，供 actual 实现与 desktopTest 共用。
 *
 * ## 为什么返回可空
 * [inspection] 为 true 时返回 null —— 语义是"此模式下**不提供**共享实例"，而不是"构造失败"。
 * 预览（`LocalInspectionMode`）各自需要自己的 context，若把它塞进进程级缓存，某个预览的
 * context 会被泄漏给随后的真实 UI。调用方据此自建实例，缓存里也就永远不会出现预览的 context。
 *
 * ## 线程安全
 * 全程持 [loaderLock]，不做无锁快路径 —— 无锁的双检需要 `@Volatile` 才成立（否则可能
 * 读到半构造对象），而本函数在生产里只由 `remember` 的缓存未命中触发，加锁开销可忽略。
 * 代价换来的是"并发首取必然只有一个实例"，由 [HanimeImageLoaderSingletonTest] 钉住。
 *
 * @param context 平台 context；只在真正构造那一次被使用。
 * @param inspection 是否为 Compose 预览/Inspection 模式，见上文。
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
                // 出口与浏览/下载同一条：URL 在 CDN 上（`vdownload.hembed.com/image/…`），
                // 同样会被 SNI 阻断。配置收在 createCdnFetchClient，见 CdnFetchClientTest。
                add(OkHttpNetworkFetcherFactory(callFactory = { createCdnFetchClient() }))
            }
            .build()
            // M5-2：图片管线真正就绪的时刻（Coil 是懒加载，这一步通常在首帧之后，属预期）。
            // 放在这里而不是各端入口：Android 没在 Application 里预建 ImageLoader，
            // 桌面虽有单例注册（coil-register），但真正用来取图的仍是这个。
            // 因为整段在锁内且只在构造时执行一次，mark 天然只打一次。
            .also {
                singletonLoader = it
                StartupTrace.mark("coil")
            }
    }
}

/**
 * 桌面 `setSingletonImageLoaderFactory` 的**共享出口**（A1.5）。
 *
 * ## 为什么要有它
 * 桌面此前在 `Main.kt` 自建 `ImageLoader.Builder(context)` + ktor3 取图器，与
 * [rememberHanimeImageLoader] 用的**不是同一份**：首页/详情走 `remember` 那份，
 * `setSingletonImageLoaderFactory` 那份另开一套内存/磁盘缓存 —— "进程级单例"在桌面上名存实亡。
 * 更糟的是它显式复用 `createHanimeHttpClient()`（API/HTML 出口，带站点 Cookie），会把
 * `hanime1_session` 绑到图床 host（`vdownload.hembed.com`）发出去。
 *
 * ## 契约
 * 返回的**就是** [singletonLoader]：与 [rememberHanimeImageLoader] 共享同一把 [loaderLock]、
 * 同一个实例，**绝不产生第二个**；出口回到 `createCdnFetchClient`（不注入站点 Cookie，
 * 见 `CdnFetchClientTest`）。
 *
 * 非 @Composable（`setSingletonImageLoaderFactory` 收的是普通 lambda），也**不带 inspection
 * 语义**（单例注册只有真实一种用途），故返回非空。
 *
 * @param context 桌面 Coil 平台 context；仅当单例尚未构造时被使用。
 */
fun sharedHanimeImageLoader(context: PlatformContext): ImageLoader =
    // inspection = false ⇒ hanimeImageLoaderOrNull 恒非空（见其 KDoc），故这里是确定性的。
    checkNotNull(hanimeImageLoaderOrNull(context, inspection = false)) {
        "非预览模式应当总能构造出共享 ImageLoader"
    }

/**
 * 复位进程级单例。**仅供测试**（生产代码不调用）。
 *
 * 单例是进程级的，用例之间必须能隔离，否则"取到新实例"这类断言会随执行顺序漂移。
 */
internal fun resetHanimeImageLoaderForTest() {
    loaderLock.withLock { singletonLoader = null }
}

@Composable
actual fun rememberHanimeImageLoader(): ImageLoader {
    val context = LocalPlatformContext.current
    val isInspectionMode = LocalInspectionMode.current
    // remember 的作用域是调用点，但它记住的是"本次 (context, inspection) 对应的结果"：
    // 真实分支下所有调用点拿到的是同一个进程级实例（见 hanimeImageLoaderOrNull）。
    return remember(context, isInspectionMode) {
        // 预览分支返回 null，这里按各自的 context 新建实例，绝不共享
        // （见 hanimeImageLoaderOrNull 的 KDoc）。
        hanimeImageLoaderOrNull(context, inspection = isInspectionMode)
            ?: ImageLoader.Builder(context).build()
    }
}
