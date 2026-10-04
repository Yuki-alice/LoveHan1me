package lovehan1me.data.network.egress

/**
 * 单条路由的健康（纯数据）。
 *
 * 熔断语义：阻断类一次即熔、其余攒够 [FAILURE_THRESHOLD] 次、
 * 冷却 [COOLDOWN_MS] 后半开，常量只此一处定义。
 */
data class SingleRouteHealth(
    val consecutiveFailures: Int = 0,
    val consecutiveSuccesses: Int = 0,
    val opened: Boolean = false,
    val openedAtMs: Long = 0L,
    /** EWMA RTT（毫秒）；-1 = 尚无样本，排序时视为未知。 */
    val ewmaRttMs: Long = -1L,
    /** 近 10 次成败位图（1 = 成功），低位是最新一次。 */
    val windowMask: Int = 0,
    val windowCount: Int = 0,
) {
    /** 近窗成功率；无样本视为全好（新路由先给机会，排序 ties 走默认表序）。 */
    fun successRate(): Double =
        if (windowCount == 0) 1.0
        else windowMask.countOneBits().toDouble() / windowCount
}

/**
 * 一域的健康：各路由健康 + 粘滞优选。纯状态机，时间由调用方传入，可离线断言。
 *
 * ## 粘滞规则（决策第 3 条）
 * - 某路由连续 [LOCK_SUCCESS_THRESHOLD] 次成功 → 锁定为优选（调度器排首位）；
 * - 锁定路由的任何失败 → 解锁，按健康重排；
 * - 熔断中的路由不参与排序，冷却到期后半开（下一次失败立刻重熔，无需再攒阈值）。
 */
data class RouteHealth(
    val routes: Map<RouteId, SingleRouteHealth> = emptyMap(),
    val lockedRoute: RouteId? = null,
) {
    fun single(route: RouteId): SingleRouteHealth = routes[route] ?: SingleRouteHealth()

    /** 该路由此刻是否熔断（不放行）。冷却到期返回 false = 半开。 */
    fun isOpen(route: RouteId, nowMs: Long): Boolean {
        val single = single(route)
        return single.opened && (nowMs - single.openedAtMs) < COOLDOWN_MS
    }

    /** 粘滞优选：已锁定且未熔断才有效，否则调度器走健康排序。 */
    fun preferred(nowMs: Long): RouteId? {
        val locked = lockedRoute ?: return null
        return if (isOpen(locked, nowMs)) null else locked
    }

    fun onResult(route: RouteId, outcome: AttemptOutcome, rttMs: Long, nowMs: Long): RouteHealth {
        val current = single(route)
        val pushedMask = ((current.windowMask shl 1) or (if (outcome == AttemptOutcome.Success) 1 else 0)) and WINDOW_MASK
        val pushedCount = minOf(current.windowCount + 1, WINDOW_SIZE)
        // 超时/取消不记 RTT（rttMs < 0 = 无样本）：成败照推进窗口，EWMA 不动。
        val sample = rttMs.takeIf { it >= 0 }
        val next = when (outcome) {
            AttemptOutcome.Success -> current.copy(
                consecutiveFailures = 0,
                consecutiveSuccesses = current.consecutiveSuccesses + 1,
                opened = false,
                openedAtMs = 0L,
                ewmaRttMs = when {
                    sample == null -> current.ewmaRttMs
                    current.ewmaRttMs < 0 -> sample
                    else -> (current.ewmaRttMs * 7 + sample) / 10
                },
                windowMask = pushedMask,
                windowCount = pushedCount,
            )
            AttemptOutcome.Blocked -> current.copy(
                consecutiveFailures = current.consecutiveFailures + 1,
                consecutiveSuccesses = 0,
                opened = true,
                openedAtMs = nowMs,
                windowMask = pushedMask,
                windowCount = pushedCount,
            )
            AttemptOutcome.TransportError, AttemptOutcome.GatewayErrorPage -> {
                val failures = current.consecutiveFailures + 1
                current.copy(
                    consecutiveFailures = failures,
                    consecutiveSuccesses = 0,
                    opened = failures >= FAILURE_THRESHOLD,
                    openedAtMs = if (failures >= FAILURE_THRESHOLD) nowMs else current.openedAtMs,
                    windowMask = pushedMask,
                    windowCount = pushedCount,
                )
            }
        }
        // 只有"无锁定 + 刚达阈值"才加锁；锁定路由失败即解锁；别家成功不抢锁（防 flap）。
        val nextLocked = when {
            route == lockedRoute && outcome != AttemptOutcome.Success -> null
            lockedRoute == null && outcome == AttemptOutcome.Success &&
                next.consecutiveSuccesses >= LOCK_SUCCESS_THRESHOLD -> route
            else -> lockedRoute
        }
        return copy(routes = routes + (route to next), lockedRoute = nextLocked)
    }

    companion object {
        /** 非阻断类失败累计到此数才熔断。 */
        const val FAILURE_THRESHOLD = 3

        /** 熔断冷却时长；到期后半开。 */
        const val COOLDOWN_MS = 5 * 60 * 1000L

        /** 连续成功到此数锁定优选。 */
        const val LOCK_SUCCESS_THRESHOLD = 3

        /** 成功率窗口宽度（位图 10 位）。 */
        const val WINDOW_SIZE = 10
        private const val WINDOW_MASK = (1 shl WINDOW_SIZE) - 1
    }
}
