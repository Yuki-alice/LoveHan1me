package lovehan1me.core.util

/**
 * 跨平台互斥锁。
 *
 * commonMain 里没有 `synchronized`（JVM 的进不了 Native），而埋点单例
 * （[PlayerTrace][lovehan1me.feature.video.PlayerTrace]、
 * [StartupTrace]）要求"可从任意线程调用"——各端入口线程本就不同
 * （Android 主线程、桌面 AWT/Compose 线程、iOS 主线程/后台队列）。
 *
 * 用法与 `synchronized(lock) { }` 同形：
 * ```
 * private val lock = PlatformLock()
 * fun mark(name: String) {
 *     val delta = lock.withLock { ... }
 * }
 * ```
 *
 * iOS 侧是真锁（`NSLock`），不是 DataStore 那种"iOS 空实现"——
 * 那边的 `withInitLock` 在 iOS 直接跑 block（初始化只走一次，敢空）；
 * 这里宁可多一次加锁，也不在语义上撒谎。
 */
expect class PlatformLock()

/**
 * 加锁执行并返回结果。inline：调用点里的非局部 return（`?: return`）
 * 能直接穿透，与 `synchronized` 同语义，旧调用点逐行替换即可。
 */
expect inline fun <T> PlatformLock.withLock(block: () -> T): T
