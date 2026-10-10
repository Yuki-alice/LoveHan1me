package lovehan1me.core.util

import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/**
 * 下载限速器（全程平均速率），对齐参考项目 `SpeedLimitInterceptor` 的 `bytesPerSecond` 语义。
 *
 * 为什么放在 commonMain 而不是 OkHttp 拦截器：三端下载链并不是同一个 HTTP 栈
 * （Android / 桌面走 OkHttp、iOS 走 Ktor Darwin），把"读了多少字节该等多久"收敛成
 * 一个平台无关的原语后，各端读循环只负责喂字节数 —— 判定不必为 iOS 另写一份。
 *
 * 不变量：累计等待 ≈ `已传字节 / 速率`。每次读完立刻算账，欠账就补睡差额；
 * 已超前（比如某次 read 很慢）不再回补，避免惩罚突发流量。`bytesPerSecond <= 0` = 不限速。
 *
 * 快照语义：速率在建实例时读取，一次下载（或一段续传循环）内保持恒定 ——
 * 用户中途改档位对**进行中**的下载不生效，下一次下载自然读到新值。
 */
class DownloadThrottle(private val bytesPerSecond: Long) {

    private val start = TimeSource.Monotonic.markNow()
    private var transferredBytes = 0L

    /** 喂入本次读到的字节数；必要时挂起补足差额。 */
    suspend fun await(bytesRead: Int) {
        if (bytesPerSecond <= 0L || bytesRead <= 0) return
        transferredBytes += bytesRead
        val waitMillis = throttleDelayMillis(
            transferredBytes = transferredBytes,
            elapsedMillis = start.elapsedNow().inWholeMilliseconds,
            bytesPerSecond = bytesPerSecond,
        )
        if (waitMillis > 0L) delay(waitMillis)
    }
}

/**
 * 纯函数：按"已传字节 / 速率"应耗费的毫秒，减去挂钟已过的毫秒；已超前返回 0（不回补）。
 *
 * 抽成纯函数是为了能确定性单测 —— 速率计算不该靠真实时钟赌时间。
 */
internal fun throttleDelayMillis(
    transferredBytes: Long,
    elapsedMillis: Long,
    bytesPerSecond: Long,
): Long {
    if (bytesPerSecond <= 0L || transferredBytes <= 0L) return 0L
    val expectedMillis = transferredBytes * 1000L / bytesPerSecond
    return (expectedMillis - elapsedMillis).coerceAtLeast(0L)
}