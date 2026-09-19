package lovehan1me.data.danmaku

import kotlinx.coroutines.flow.Flow
import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.core.util.LogUtil
import lovehan1me.core.util.PlatformLock
import lovehan1me.core.util.withLock
import lovehan1me.data.database.dao.DanmakuDao
import lovehan1me.data.database.dao.Han1meDatabases
import lovehan1me.data.database.entity.DanmakuCacheEntity
import lovehan1me.data.database.entity.DanmakuMappingEntity
import kotlin.coroutines.cancellation.CancellationException

/**
 * 一次弹幕装载的结果。
 *
 * 四个分支对应状态条上要说的四句不同的话。**全部静默**：任何一支都不 toast、不弹错误框，
 * 弹幕是锦上添花，落空不该打断播放。
 */
sealed interface DanmakuLoadResult {

    data class Ready(val episode: DanmakuEpisodeRef, val items: List<DanmakuItem>) : DanmakuLoadResult

    /** 已关联、但源里这一集确实没有弹幕 —— 说「暂无弹幕」，而不是「未关联」。 */
    data class Empty(val episode: DanmakuEpisodeRef) : DanmakuLoadResult

    /** 没有关联记录，自动匹配也没敢猜。这是「点击关联」入口的唯一触发条件。 */
    data object Unmatched : DanmakuLoadResult

    /** 拉取失败且无可用缓存。[message] 只进日志，不面向用户。 */
    data class Failed(val message: String) : DanmakuLoadResult
}

/**
 * 弹幕仓库：关联记录（用户资产，永久）↔ 弹幕缓存（可丢弃镜像，带 TTL）↔ [DanmakuProvider]。
 *
 * resolve 的三级顺序就是"命中率低"这个事实的产物：
 *  1. **关联记录命中** —— 主路径。人工选过一次就永远不再猜。
 *  2. 自动匹配 —— 只在唯一高置信时落库（`manual = false`），有歧义直接跳过。
 *  3. 都没有 → [DanmakuLoadResult.Unmatched]，交给状态条上的手动入口。
 *
 * 缓存的两条硬约束：
 *  - **有序**：读出时按 `playTimeMillis, cid` 升序，弹幕引擎的发射扫描靠二分游标。
 *  - **空结果也要记**：源里真没弹幕时写一条哨兵行，否则每次播放都要重跑一遍注定为空的请求。
 */
object DanmakuRepository {

    /** 弹幕缓存有效期。一部片子的弹幕在几分钟内不会变化，12 小时已经保守。 */
    private const val CACHE_TTL_MILLIS = 12 * 60 * 60 * 1000L

    /** 拉取失败后的冷静期，避免"每次 seek 都撞一次死接口"。 */
    private const val FAIL_COOLDOWN_MILLIS = 10 * 60 * 1000L

    /** 超过这个年龄的缓存行直接回收（关联记录不受影响）。 */
    private const val RETENTION_MILLIS = 30 * 24 * 60 * 60 * 1000L

    /** 空结果哨兵：cid 取 0（远端 cid 从 1 起），读出时被过滤掉。 */
    private const val TOMBSTONE_CID = 0L

    /**
     * 走 getter 而不是 `private val dao = ...`：本 object 可能在
     * `Han1meDatabaseContext` 拿到 Context 之前就被类加载摸到（各端初始化时序不同），
     * 提前建库会炸。懒一点没有成本。
     */
    private val dao: DanmakuDao get() = Han1meDatabases.danmaku.danmakuDao

    private val lock = PlatformLock()
    private val cooldownUntil = mutableMapOf<String, Long>()
    private var maintenanceDone = false

    /**
     * 自动匹配落空的阴性记忆：同一 videoCode 在 TTL 内不再重跑整条匹配漏斗。
     *
     * 漏斗放宽后落空路径的请求数是以前的 2~3 倍（多查询词 + 逐候选试对 + 直搜兜底），
     * 而里番落空本就是常态 —— 不记的话每次进播放器都要空转一遍。
     * 只记"猜过且没猜中"：人工关联/取消关联会清掉它（见 [link]/[unlink]），
     * 下次进页面走关联记录，不再经过这里。
     */
    private val autoMiss = DanmakuAutoMissCache()

    /**
     * 播放这一集时问一次弹幕。
     *
     * [rawTitle] 是视频页原始标题，只有走到自动匹配那一级才会用到（内部再清洗）。
     */
    suspend fun resolve(
        videoCode: String,
        rawTitle: String,
        provider: DanmakuProvider,
    ): DanmakuLoadResult {
        val bound = dao.getMapping(videoCode)?.takeIf { it.providerId == provider.id }
        if (bound != null) return load(provider, bound.toRef())

        // 阴性记忆命中：上次已经完整跑过一遍漏斗且没猜中，直接交手动入口，不碰网络。
        if (autoMiss.isFresh(videoCode)) return DanmakuLoadResult.Unmatched

        val guess = try {
            provider.autoMatch(rawTitle)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            LogUtil.w("弹幕自动匹配异常 videoCode=$videoCode: ${error.message}", error)
            null
        }
        if (guess == null) {
            // 异常与"没猜中"都记：前者下次重试由 TTL 到期驱动，后者同理；
            // 区分两者没有意义 —— 都是"现在没有关联"。
            autoMiss.mark(videoCode)
            return DanmakuLoadResult.Unmatched
        }

        dao.upsertMapping(guess.toEntity(videoCode, provider.id, manual = false))
        return load(provider, guess)
    }

    /**
     * 人工把当前视频关联到某一集。`manual = true` 的记录不会被自动匹配覆盖。
     *
     * 返回首次装载结果，好让 UI 立刻知道关联对不对（而不是等下次进页面）。
     */
    suspend fun link(
        videoCode: String,
        provider: DanmakuProvider,
        episode: DanmakuEpisodeRef,
    ): DanmakuLoadResult {
        dao.upsertMapping(episode.toEntity(videoCode, provider.id, manual = true))
        // 已有 mapping 的 resolve 根本走不到阴性记忆（先判关联记录），这里清掉只是卫生：
        // 免得 unlink 后这条旧落空记忆还在 TTL 内，白白跳过一次值得重跑的漏斗。
        autoMiss.clear(videoCode)
        // 冷却是"接口刚才挂了"的临时惩罚，用户手点的重试该立刻放行；
        // 目标集的新鲜缓存则照用 —— 它就键在这一集上，清了等于白拉。
        clearCooldown(provider.id, episode.episodeId)
        return load(provider, episode)
    }

    suspend fun unlink(videoCode: String) {
        dao.deleteMapping(videoCode)
        // 关联删了：下次进页面值得重跑一遍漏斗（用户可能换了标题写法或库里新上了这部）。
        autoMiss.clear(videoCode)
    }

    /** 状态条要显示「已关联：xxx」，用 Flow 而不是每次重读。 */
    fun observeMapping(videoCode: String): Flow<DanmakuMappingEntity?> =
        dao.observeMapping(videoCode)

    // ---------- 缓存 ----------

    private suspend fun load(
        provider: DanmakuProvider,
        episode: DanmakuEpisodeRef,
    ): DanmakuLoadResult {
        evictOnce()
        val providerId = provider.id
        val episodeId = episode.episodeId
        val now = currentEpochMillis()
        val cachedAt = dao.cachedAt(providerId, episodeId)
        if (cachedAt != null && now - cachedAt < CACHE_TTL_MILLIS) return fromCache(providerId, episode)

        if (isCoolingDown(providerId, episodeId)) {
            // 冷却期里宁可用过期缓存 —— 旧弹幕远好过没弹幕
            return if (cachedAt != null) fromCache(providerId, episode)
            else DanmakuLoadResult.Failed("冷却中")
        }

        val items = try {
            provider.fetch(episodeId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            markCooldown(providerId, episodeId)
            LogUtil.w("弹幕拉取失败 episode=$episodeId: ${error.message}", error)
            // 失败时同样回退到过期缓存
            return if (cachedAt != null) fromCache(providerId, episode)
            else DanmakuLoadResult.Failed(error.message.orEmpty())
        }

        writeCache(providerId, episodeId, items, now)
        clearCooldown(providerId, episodeId)
        return if (items.isEmpty()) DanmakuLoadResult.Empty(episode)
        else DanmakuLoadResult.Ready(episode, items)
    }

    private suspend fun fromCache(
        providerId: String,
        episode: DanmakuEpisodeRef,
    ): DanmakuLoadResult {
        val items = dao.getDanmaku(providerId, episode.episodeId)
            .filterNot { it.cid == TOMBSTONE_CID }
            .map { it.toItem() }
        return if (items.isEmpty()) DanmakuLoadResult.Empty(episode)
        else DanmakuLoadResult.Ready(episode, items)
    }

    /**
     * 整集替换。
     *
     * delete + insert 之间没有事务（见 [DanmakuDao.clearEpisode]），中途被杀只会让下次重拉。
     */
    private suspend fun writeCache(
        providerId: String,
        episodeId: String,
        items: List<DanmakuItem>,
        fetchedAt: Long,
    ) {
        dao.clearEpisode(providerId, episodeId)
        dao.insertDanmaku(
            if (items.isEmpty()) {
                listOf(
                    DanmakuCacheEntity(
                        providerId = providerId,
                        episodeId = episodeId,
                        cid = TOMBSTONE_CID,
                        playTimeMillis = 0L,
                        location = DanmakuLocation.SCROLL.name,
                        color = 0,
                        text = "",
                        fetchedAt = fetchedAt,
                    ),
                )
            } else {
                items.map {
                    DanmakuCacheEntity(
                        providerId = providerId,
                        episodeId = episodeId,
                        cid = it.id,
                        playTimeMillis = it.playTimeMillis,
                        location = it.location.name,
                        color = it.color,
                        text = it.text,
                        fetchedAt = fetchedAt,
                    )
                }
            },
        )
    }

    /** 进程内只做一次的全量回收：项目没有统一的启动清理钩子，挂在这里最合适。 */
    private suspend fun evictOnce() {
        val alreadyDone = lock.withLock { maintenanceDone }
        if (alreadyDone) return
        // 无索引列上的全表 DELETE，一进程一次足够，不能放进每次 resolve
        dao.evictOlderThan(currentEpochMillis() - RETENTION_MILLIS)
        lock.withLock { maintenanceDone = true }
    }

    // ---------- 失败冷却（内存态：重启即清，代价只是重发一次请求） ----------

    private fun isCoolingDown(providerId: String, episodeId: String): Boolean = lock.withLock {
        val until = cooldownUntil[key(providerId, episodeId)] ?: return@withLock false
        if (currentEpochMillis() >= until) {
            cooldownUntil.remove(key(providerId, episodeId))
            false
        } else {
            true
        }
    }

    private fun markCooldown(providerId: String, episodeId: String) {
        lock.withLock { cooldownUntil[key(providerId, episodeId)] = currentEpochMillis() + FAIL_COOLDOWN_MILLIS }
    }

    private fun clearCooldown(providerId: String, episodeId: String) {
        lock.withLock { cooldownUntil.remove(key(providerId, episodeId)) }
    }

    private fun key(providerId: String, episodeId: String): String = "$providerId:$episodeId"
}

/**
 * 自动匹配落空的阴性记忆（内存态：重启即清，代价只是重跑一次漏斗）。
 *
 * `now` 做成参数只为单测注入假时钟，生产用默认值。
 */
internal class DanmakuAutoMissCache(
    private val ttlMillis: Long = AUTO_MISS_TTL_MILLIS,
    private val now: () -> Long = { currentEpochMillis() },
) {
    private val marks = mutableMapOf<String, Long>()

    /** 该 videoCode 的落空记忆是否还在 TTL 内（过期顺手清掉，map 不会无限涨）。 */
    fun isFresh(videoCode: String): Boolean {
        val at = marks[videoCode] ?: return false
        if (now() - at >= ttlMillis) {
            marks.remove(videoCode)
            return false
        }
        return true
    }

    fun mark(videoCode: String) {
        marks[videoCode] = now()
    }

    fun clear(videoCode: String) {
        marks.remove(videoCode)
    }
}

/** 落空记忆有效期：一部片子 24h 内不会从"库里没有"变成"库里有"到值得每次进页面都重搜。 */
private const val AUTO_MISS_TTL_MILLIS = 24 * 60 * 60 * 1000L

// ---------- 映射 ----------

private fun DanmakuMappingEntity.toRef(): DanmakuEpisodeRef = DanmakuEpisodeRef(
    episodeId = episodeId,
    episodeTitle = episodeTitle,
    subjectId = subjectId,
    subjectTitle = subjectTitle,
)

private fun DanmakuEpisodeRef.toEntity(
    videoCode: String,
    providerId: String,
    manual: Boolean,
): DanmakuMappingEntity = DanmakuMappingEntity(
    videoCode = videoCode,
    providerId = providerId,
    subjectId = subjectId,
    subjectTitle = subjectTitle,
    episodeId = episodeId,
    episodeTitle = episodeTitle,
    manual = manual,
    createdAt = currentEpochMillis(),
)

/** 缓存行 → 引擎模型。[DanmakuCacheEntity] 刻意不带 `isSelf`，v1 全是远端弹幕。 */
private fun DanmakuCacheEntity.toItem(): DanmakuItem = DanmakuItem(
    id = cid,
    playTimeMillis = playTimeMillis,
    text = text,
    color = color,
    location = location.let { name -> DanmakuLocation.entries.firstOrNull { it.name == name } }
        ?: DanmakuLocation.SCROLL,
    isSelf = false,
)
