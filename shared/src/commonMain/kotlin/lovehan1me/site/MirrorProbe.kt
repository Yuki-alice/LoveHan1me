package lovehan1me.site

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.data.network.egress.MirrorRegistry
import lovehan1me.data.network.egress.RouteId

/**
 * 镜像自动选优（F10/B3-3）。
 *
 * 只回答一个问题：**此刻该用哪个镜像**。切换动作本身不在这里（见 [SiteSwitcher]）——
 * 选优与切换分开，是为了让"选得对不对"能被离线断言，而不必真的发请求、真的切站。
 */
object MirrorProbe {

    /**
     * 并发探测各镜像的往返延迟。
     *
     * @param probeOne 探测单个镜像（RTT 毫秒；不可达 / 超时返回 null）。
     *   **由调用方注入**：探测要发真实请求，而"用哪个 client"是上层的事。
     *   判定逻辑因此保持可离线断言（本仓的一贯做法，见 `EgressScheduler` 的健康注入）。
     *
     * 一个镜像探测失败**不影响其他镜像** —— 目的是"挑出最快的那个"，
     * 不是"给所有镜像出一份体检报告"，个别失败不该让整体拿不到答案。
     */
    suspend fun probe(
        candidates: List<String>,
        probeOne: suspend (String) -> Long?,
    ): Map<String, Long> = coroutineScope {
        candidates
            .map { host -> async { host to probeOne(host) } }
            .awaitAll()
            .mapNotNull { (host, rtt) -> rtt?.let { host to it } }
            .toMap()
    }

    /**
     * 选出最优镜像。
     *
     * 判定顺序（刻意写死，便于断言）：
     * 1. 档案里**已熔断**的先排除 —— 一个正在冷却期的镜像，就算这一刻延迟最低也不该选它，
     *    那是"刚恢复就被再打一次"；
     * 2. 若**全部**熔断，仍要给出一个答案（回退到全部候选）—— 返回 null 会让调用方无从下手，
     *    而"最坏的那个"也好过"没有"；
     * 3. 有探测数据 → 按 RTT 升序取首个（ties 取候选表里的靠前项）；
     * 4. 无探测数据（没探测，或全部不可达）→ 回退档案排序 [MirrorRegistry.rankMirrors]。
     *    这一步是 B3-2 存在的意义：**探测失败时仍有依据**，不至于每次从零瞎选。
     */
    fun bestOf(
        candidates: List<String>,
        probeResult: Map<String, Long> = emptyMap(),
        nowMs: Long = currentEpochMillis(),
    ): String? {
        if (candidates.isEmpty()) return null
        val usable = candidates
            .filter { !MirrorRegistry.healthOf(it).isOpen(RouteId.Gate, nowMs) }
            .ifEmpty { candidates }

        val measured = usable.mapNotNull { host -> probeResult[host]?.let { host to it } }
        if (measured.isEmpty()) return MirrorRegistry.rankMirrors(usable, nowMs).firstOrNull()
        // minBy 取首个最小值：与 sortedWith 的稳定排序同效果，且少一次整体排序。
        return measured.minBy { (_, rtt) -> rtt }.first
    }
}
