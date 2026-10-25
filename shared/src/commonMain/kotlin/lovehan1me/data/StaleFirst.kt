package lovehan1me.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * stale-first 读取骨架（网络优化 3.1）：先发缓存值（读失败/无值静默跳过），
 * 再透传网络流。首页/发现页"先展陈旧内容再刷新"共用这一段；
 * token / merge / 刷新语义留在各自 VM（SearchViewModel 的 PagingGate 判定、
 * 首页的 StartupTrace 埋点都不进骨架 —— 骨架只回答"先发什么、再发什么"）。
 *
 * 双类型参数是刻意的：首页的缓存产出是解析好的 [HomePage]、网络产出是
 * `WebsiteState<HomePage>`（错误态也在网络侧），两类本来就不同构 ——
 * 强行共用 T 只会把转型灾难转嫁给每个调用方。sealed 分派保证两个分支
 * 各自拿到准确类型，无需 cast。
 *
 * 读缓存是挂起的：期间调用方可能已被 cancel，stale 产出由调用方自行的
 * 闸门/守卫裁决是否采纳（与 3.1 之前手写的时序一致）。
 */
sealed interface StaleOrFresh<out S, out F> {
    /** 缓存先出（可能陈旧），类型 [S]。 */
    data class Stale<S>(val value: S) : StaleOrFresh<S, Nothing>

    /** 网络产出（成功与失败都走这里，调用方按原有语义处理），类型 [F]。 */
    data class Fresh<F>(val value: F) : StaleOrFresh<Nothing, F>
}

fun <S, F> staleFirstFlow(
    readStale: suspend () -> S?,
    network: Flow<F>,
): Flow<StaleOrFresh<S, F>> = flow {
    runCatching { readStale() }.getOrNull()?.let { emit(StaleOrFresh.Stale(it)) }
    network.collect { emit(StaleOrFresh.Fresh(it)) }
}
