package lovehan1me.data.network

import lovehan1me.core.util.LogUtil
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * 内置 / 自定义 IP 池的**连通性探测 + 短缓存**。
 *
 * ## 为什么需要
 * 内置 IP 是硬编码的（`HanimeDns.cloudFlareIps`），会随时间失效。实测（2026-09-21
 * 本机）5 个里只有 3 个通（170–230ms），另外 2 个 443 握手直接超时。把整张表原样
 * 交给 OkHttp 的后果不是"慢一点"，而是**先卡在两个死 IP 的连接超时上**——用户看到
 * 的就是首页一直转圈。
 *
 * 所以自动档（`AppSettings.autoBuiltInHosts`）不能"直接返回整张表"，必须先挑出
 * 真正能建连的那些，再按延迟排序。
 *
 * ## 为什么是 TCP 443 握手，不是 `InetAddress.isReachable`
 * 后者在拿不到 raw socket 权限时（Windows 常见）退化成 **port 7 echo** 探测，对着
 * Cloudflare 边缘 IP 恒返回 false——拿它做筛选会把所有节点都判死，自动档就永远
 * 回退、等于没开。同一批 IP 用 443 握手则能拿到 170–230ms 的真实延迟。
 *
 * ## 调用时机
 * 调用方（[HanimeDns]）跑在 OkHttp 的**网络线程**上，不是主线程，所以这里可以阻塞；
 * 探测是并发的，最坏耗时约 [PROBE_TIMEOUT_MS]，只在缓存过期后的第一次解析付出。
 */
object CdnIpProbe {

    private const val PROBE_TIMEOUT_MS = 1200
    private const val CACHE_TTL_MS = 10 * 60 * 1000L
    private const val PROBE_PORT = 443

    @Volatile
    private var cachedKey: String = ""

    @Volatile
    private var cachedIps: List<String> = emptyList()

    @Volatile
    private var cachedAtMs: Long = 0L

    /**
     * 返回 [ips] 里**能建连**的那些，按握手耗时升序。
     *
     * 一个都不通时返回**空表**——这是有意的：调用方拿到空表就知道该回退到
     * DoH / 系统 DNS，而不是拿着一张全死的 IP 表去撞连接超时。
     */
    fun usable(ips: List<String>): List<String> {
        val candidates = ips.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (candidates.isEmpty()) return emptyList()

        val key = candidates.joinToString(",")
        val now = System.currentTimeMillis()
        if (key == cachedKey && cachedIps.isNotEmpty() && now - cachedAtMs < CACHE_TTL_MS) {
            return cachedIps
        }

        val latencies = ConcurrentHashMap<String, Int>()
        val latch = CountDownLatch(candidates.size)
        for (ip in candidates) {
            thread(start = true, isDaemon = true, name = "cdn-probe-$ip") {
                try {
                    val start = System.currentTimeMillis()
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(InetAddress.getByName(ip), PROBE_PORT), PROBE_TIMEOUT_MS)
                    }
                    latencies[ip] = (System.currentTimeMillis() - start).toInt()
                } catch (_: Exception) {
                    // 连不上就不进结果表：留空让调用方回退，别把死 IP 交出去。
                } finally {
                    latch.countDown()
                }
            }
        }
        // 每个探测线程自带超时；这里的上限只是兜底，防某个线程卡在地址解析上。
        latch.await(PROBE_TIMEOUT_MS + 1000L, TimeUnit.MILLISECONDS)

        val sorted = latencies.entries.sortedBy { it.value }.map { it.key }
        if (sorted.isNotEmpty()) {
            cachedKey = key
            cachedIps = sorted
            cachedAtMs = now
            LogUtil.i("DNS", "CDN 探测可用 ${sorted.size}/${candidates.size}：$sorted")
        } else {
            LogUtil.w("DNS", "CDN 探测全部失败（${candidates.size} 个），本次回退系统解析")
        }
        return sorted
    }

    /** 改了自定义 IP / 点了重新测速后调用，下次解析会重新探测。 */
    fun invalidate() {
        cachedKey = ""
        cachedIps = emptyList()
        cachedAtMs = 0L
    }
}
