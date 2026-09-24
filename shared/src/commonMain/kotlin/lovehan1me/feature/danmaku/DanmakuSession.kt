package lovehan1me.feature.danmaku

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lovehan1me.core.domain.model.VideoComments
import lovehan1me.core.util.LogUtil
import lovehan1me.data.danmaku.CommentDanmakuMapper
import lovehan1me.data.danmaku.DanmakuEpisodeRef
import lovehan1me.data.danmaku.DanmakuItem
import lovehan1me.data.danmaku.DanmakuLoadResult
import lovehan1me.data.danmaku.DanmakuLocation
import lovehan1me.data.danmaku.DanmakuProvider
import lovehan1me.data.danmaku.DanmakuRepository
import lovehan1me.data.danmaku.DanmakuSubject
import kotlin.coroutines.cancellation.CancellationException

/**
 * 状态条要说的话。**一个分支一句话**，都不带 toast：
 * 弹幕落空是常态，打断播放才是事故。
 */
sealed interface DanmakuStatus {

    /** 用户把总开关关了（此时连请求都不发）。 */
    data object Disabled : DanmakuStatus

    data object Loading : DanmakuStatus

    /** 没有关联记录，自动匹配也没敢猜 → 「点击检索」入口。 */
    data object Unmatched : DanmakuStatus

    /** 已关联，但源里这一集就是 0 条 —— 与"没关联"是两句话，别混。 */
    data object NoDanmaku : DanmakuStatus

    /** 拉取失败。静默：只让状态条显示"暂不可用"，不弹任何东西。 */
    data object Unavailable : DanmakuStatus

    /**
     * 弹弹已关联（精确时间轴）+ 站内评论（氛围）。`remoteCount` 为 0 也属此态 ——
     * "关联上了但源里没弹幕、评论顶着"是另一种完整，不要和 [NoDanmaku] 混。
     */
    data class Linked(
        val episode: DanmakuEpisodeRef,
        val remoteCount: Int,
        val commentCount: Int = 0,
    ) : DanmakuStatus

    /**
     * 只有评论投影（弹弹没配 / 没关联 / 没拉到 / 被充足门跳过）。
     * 点击仍进选集弹窗 —— 手动关联弹弹的路永远留着。
     */
    data class CommentOnly(val commentCount: Int) : DanmakuStatus
}

/**
 * 一个视频页一个 session：把「仓库 / 位置时钟 / 引擎」拧成一件事。
 *
 * 持有时机由 `rememberDanmakuSession` 决定（本仓的既有习惯是"纯逻辑类 + 屏幕持有"，
 * 不是每个功能塞一个 ViewModel）。它刻意**不依赖播放器类型** ——
 * 状态由 [onPlaybackSnapshot] 以裸参数喂进来，于是这条链在 desktopTest 里可全量覆盖。
 *
 * [advance] 是绘制循环的唯一入口，帧回调之外没有第二个时钟源：
 * 引擎的发射判断只看视频时间（见 [DanmakuEngine]），所以"谁在什么时刻调它"
 * 不影响布局结果，只影响最多一帧的入场延迟。
 */
class DanmakuSession(
    val videoCode: String,
    /** 站内原始标题。人工选集弹窗拿它当检索关键字（用户十次有十次就是要找这部）。 */
    val title: String,
    private val provider: DanmakuProvider,
    private val scope: CoroutineScope,
    /** 仓库侧的三个动作做成参数：单测注入假的，生产用默认值。 */
    private val resolve: suspend (String, String, DanmakuProvider) -> DanmakuLoadResult =
        DanmakuRepository::resolve,
    private val linkTo: suspend (String, DanmakuProvider, DanmakuEpisodeRef) -> DanmakuLoadResult =
        DanmakuRepository::link,
    private val unlinkFrom: suspend (String) -> Unit = DanmakuRepository::unlink,
    val tracker: DanmakuPositionTracker = DanmakuPositionTracker(),
    override val engine: DanmakuEngine = DanmakuEngine(),
) : DanmakuFrameDriver {

    private val mutableStatus = MutableStateFlow<DanmakuStatus>(DanmakuStatus.Loading)
    val status: StateFlow<DanmakuStatus> = mutableStatus.asStateFlow()

    /** 用户总开关。关掉时不请求、不发射；[status] 呈现 [DanmakuStatus.Disabled]。 */
    var enabled: Boolean = true
        private set

    /**
     * 评论投影开关（主源）。关掉时只剩弹弹那路；[DanmakuProvider.from] 返回 null
     * （弹弹未配置）且这里也关掉时，调用方应连 session 都不构造（见 `rememberDanmakuSession`）。
     */
    var commentEnabled: Boolean = true
        private set

    private var loadAttempted = false
    private var loadJob: Job? = null
    private var lastPositionMs = 0L
    private var lastDurationMs = 0L
    private var lastSeekGeneration = 0L

    /** 弹弹那路装载的一集（增强层）；null = 没关联/没拉到/被充足门跳过。 */
    private var dandanEpisode: DanmakuEpisodeRef? = null
    private var dandanItems: List<DanmakuItem> = emptyList()
    private var dandanFailed = false

    /** 评论那路映射好的弹幕（主源）：重算只看 key 集合 + 片长，点赞数变化不触发重排。 */
    private var rawComments: List<VideoComments.VideoComment> = emptyList()
    private var commentItems: List<DanmakuItem> = emptyList()
    private var commentKeys: Set<String> = emptySet()
    private var commentDurationMs = 0L

    /**
     * 按类型可见（滚动 / 顶部 / 底部，Kazumi 对齐）。
     *
     * 默认全开：session 构造时还不知道设置值，`rememberDanmakuSession` 紧接着
     * 就会用设置覆盖；构造与 effect 之间那一帧按全开处理，不丢东西。
     * 评论投影全是滚动，关滚动会连评论一起藏 —— 调用方（设置弹窗）应把话说清楚，
     * 这里只做过滤，不替用户操心。
     */
    private var visibleLocations: Set<DanmakuLocation> = DanmakuLocation.entries.toSet()

    /** 每次装载后只放行**一行**心跳日志 —— [advance] 是 60Hz 的帧循环，不能每帧都打。 */
    private var heartbeatArmed = false

    /**
     * 开始装载。**幂等**：位置采样每 250ms 来一次，不能每次都重发请求。
     *
     * 评论充足时跳过弹弹自动匹配（见 [CommentDanmakuMapper.COMMENT_ABUNDANT_THRESHOLD]）：
     * 热门视频评论本来就够热，共享额度省给冷门视频。手动关联不受影响，
     * 评论变少（如开关重开）后 [reload] 会重走这里。
     */
    fun start() {
        if (!enabled || loadAttempted || loadJob?.isActive == true) return
        if (commentEnabled && commentItems.size >= CommentDanmakuMapper.COMMENT_ABUNDANT_THRESHOLD) {
            loadAttempted = true
            LogUtil.d(TAG, "评论充足（${commentItems.size} 条），跳过弹弹自动匹配")
            refreshStatus()
            return
        }
        loadAttempted = true
        dandanFailed = false
        // 评论在屏时不闪"加载中"：refreshStatus 会在无评论时落到 Loading
        refreshStatus()
        loadJob = scope.launch {
            val result = try {
                resolve(videoCode, title, provider)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                LogUtil.w("弹幕装载异常 videoCode=$videoCode: ${error.message}", error)
                DanmakuLoadResult.Failed(error.message.orEmpty())
            }
            when (result) {
                is DanmakuLoadResult.Ready -> applyLoaded(result.episode, result.items)
                is DanmakuLoadResult.Empty -> applyLoaded(result.episode, emptyList())
                DanmakuLoadResult.Unmatched -> {
                    dandanEpisode = null
                    dandanItems = emptyList()
                    refreshStatus()
                }
                is DanmakuLoadResult.Failed -> {
                    dandanFailed = true
                    refreshStatus()
                    // 失败不是"试过了"，下次位置采样还要再试（仓库侧有冷却兜着）
                    loadAttempted = false
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        if (enabled) {
            // 重新打开必须重走一遍：`start()` 有 loadAttempted 幂等门，
            // 直接调它会让状态条永远卡在「已关闭」。仓库侧有 TTL，重走基本是命中缓存。
            reload()
            // 评论是本地映射：立刻重铺，不必等弹弹那路回来
            remerge()
        } else {
            engine.reset()
            mutableStatus.value = DanmakuStatus.Disabled
        }
    }

    /**
     * 评论投影开关。关掉时清空已映射的评论并让弹弹那路重走
     * （之前可能被充足门跳过）；打开时立刻重映射、重铺。
     */
    fun setCommentEnabled(enabled: Boolean) {
        if (commentEnabled == enabled) return
        commentEnabled = enabled
        if (enabled) {
            maybeRemapComments()
            remerge()
            start()
        } else {
            commentItems = emptyList()
            commentKeys = emptySet()
            commentDurationMs = 0L
            remerge()
            reload()
        }
    }

    /** 评论区拉到新列表时调一次；映射幂等（key 集合 + 片长没变直接返回）。 */
    fun setComments(comments: List<VideoComments.VideoComment>) {
        rawComments = comments
        maybeRemapComments()
    }

    /** 类型开关变化时调一次：相同组合直接返回，否则重铺（屏上旧布局作废，游标不动）。 */
    fun setLocationVisibility(scroll: Boolean, top: Boolean, bottom: Boolean) {
        val next = buildSet {
            if (scroll) add(DanmakuLocation.SCROLL)
            if (top) add(DanmakuLocation.TOP)
            if (bottom) add(DanmakuLocation.BOTTOM)
        }
        if (next == visibleLocations) return
        visibleLocations = next
        remerge()
    }

    /** 关联状态变了（人工选集 / 取消关联）之后重走一遍。 */
    fun reload() {
        loadAttempted = false
        start()
    }

    /**
     * 播放状态的一次快照。
     *
     * @param frozen 暂停 / 缓冲 / 卡顿 / 切画质 —— 时钟停摆但**不清空**弹幕
     * @param reset phase 离开 Ready：位置彻底失去参考意义，整套丢掉重来
     */
    fun onPlaybackSnapshot(
        positionMs: Long,
        durationMs: Long,
        playbackSpeed: Float,
        frozen: Boolean,
        reset: Boolean = false,
    ) {
        if (reset) {
            tracker.reset()
            engine.reset()
            loadAttempted = false
            lastPositionMs = 0L
            // 列表留着（关联记录没变，重 resolve 基本命中缓存），只重铺时间轴
            remerge()
            return
        }
        lastPositionMs = positionMs
        if (durationMs > 0L && durationMs != lastDurationMs) {
            lastDurationMs = durationMs
            // 片长刚拿到：评论映射一直等它，到了就铺
            maybeRemapComments()
        }
        tracker.onSample(positionMs, playbackSpeed, durationMs)
        tracker.setFrozen(frozen)
        start()
    }

    /**
     * 帧循环入口。
     *
     * @param frameNanos `withFrameNanos` 给的帧时刻，必须每帧同源
     * @return 本次使用的播放位置；null = 尚无锚点或已关闭，调用方什么都不画
     */
    override fun advance(
        frameNanos: Long,
        viewport: DanmakuViewport,
        measureWidth: (DanmakuItem) -> Float,
    ): Long? {
        if (!enabled) return null
        val positionMs = tracker.positionAt(frameNanos)
        if (heartbeatArmed) {
            heartbeatArmed = false
            LogUtil.d(
                TAG,
                if (positionMs == null) {
                    "弹幕帧循环已进入，但位置追踪器还没有锚点（未收到播放快照）"
                } else {
                    "弹幕帧循环 position=${positionMs}ms " +
                        "视口=${viewport.widthPx}x${viewport.heightPx}/行高${viewport.lineHeightPx} " +
                        "屏上=${engine.activeScrollSlots.size + engine.activeFixedSlots.size} " +
                        "发射=${engine.emittedCount} 丢弃=${engine.droppedCount} 游标=${engine.cursorIndex}"
                },
            )
        }
        if (positionMs == null) return null
        lastPositionMs = positionMs
        val generation = tracker.seekGeneration
        if (generation != lastSeekGeneration) {
            lastSeekGeneration = generation
            engine.seekTo(positionMs)
        }
        engine.tick(positionMs, viewport, measureWidth)
        return positionMs
    }

    // ---------- 人工选集（状态条与选集弹窗用） ----------

    /**
     * 检索候选。**异常原样上抛**：这是用户主动点的搜索，
     * "没搜到"和"搜不了"必须区分得开 —— 静默降级只适用于自动那条路。
     */
    suspend fun searchSubjects(keyword: String): List<DanmakuSubject> =
        provider.searchSubjects(keyword)

    suspend fun searchEpisodes(subjectTitle: String): List<DanmakuEpisodeRef> =
        provider.searchEpisodes(subjectTitle)

    /** 用户选定一集：落库（manual=true）并立刻装载，让状态条当场给出答案。 */
    suspend fun link(episode: DanmakuEpisodeRef) {
        loadAttempted = true
        val result = linkTo(videoCode, provider, episode)
        when (result) {
            is DanmakuLoadResult.Ready -> applyLoaded(result.episode, result.items)
            is DanmakuLoadResult.Empty -> applyLoaded(result.episode, emptyList())
            DanmakuLoadResult.Unmatched -> {
                dandanFailed = false
                refreshStatus()
            }
            is DanmakuLoadResult.Failed -> {
                dandanFailed = true
                refreshStatus()
            }
        }
    }

    suspend fun unlink() {
        unlinkFrom(videoCode)
        dandanEpisode = null
        dandanItems = emptyList()
        dandanFailed = false
        engine.reset()
        reload()
        // 评论不受关联影响：立刻重铺，别等弹弹那路回来
        remerge()
    }

    fun dispose() {
        loadJob?.cancel()
        loadJob = null
    }

    private fun applyLoaded(episode: DanmakuEpisodeRef, items: List<DanmakuItem>) {
        val startMs = lastPositionMs
        dandanEpisode = episode
        dandanItems = items
        dandanFailed = false
        // "弹幕没飘"有两种成因，光看界面分不开：一条都没拉到，还是拉到了但
        // 整段都被起点甩在后面（例如从 8:47 处关联了第 1 话，全部弹幕都在前 8 分钟）。
        LogUtil.d(
            TAG,
            "弹幕装载完成 ${episode.subjectTitle} / ${episode.episodeTitle}：远端 ${items.size} 条" +
                "（起点之前 ${items.size - items.count { it.playTimeMillis >= startMs }} 条不再回放），" +
                "评论 ${commentItems.size} 条，起点 ${startMs}ms",
        )
        heartbeatArmed = true
        remerge()
    }

    /**
     * 双源合并 + 状态重算。本层唯一的"写引擎"点（`setItems` 会重建游标，
     * 所以只在列表真正变化时调 —— 见 [maybeRemapComments] 的幂等门）。
     */
    private fun remerge() {
        if (!enabled) {
            mutableStatus.value = DanmakuStatus.Disabled
            return
        }
        val merged = mergeDanmakuItems(dandanItems, commentItems, visibleLocations)
        engine.setItems(merged, startPositionMs = lastPositionMs)
        refreshStatus()
    }

    /**
     * 状态只有一个真相来源：弹弹关联 × 评论列表。
     *
     * 优先级即诚实度：有关联就说关联（评论数一并报），没关联但有评论就说评论，
     * 都没有才说没关联/不可用。加载中途有评论在屏就不闪"加载中"。
     */
    private fun refreshStatus() {
        if (!enabled) {
            mutableStatus.value = DanmakuStatus.Disabled
            return
        }
        val episode = dandanEpisode
        mutableStatus.value = resolveDanmakuStatus(
            enabled = enabled,
            episode = episode,
            dandanCount = dandanItems.size,
            commentCount = commentItems.size,
            dandanFailed = dandanFailed,
            loadAttempted = loadAttempted,
            jobActive = loadJob?.isActive == true,
        )
    }

    /**
     * 评论重映射（幂等）：开关开着、片长已知、且"key 集合或片长"变了才真算。
     *
     * 点赞数变化不触发重排 —— `stableKey` 里本来就没有点赞，
     * 一次点赞就让整屏弹幕换时间，属于没事找事。
     */
    private fun maybeRemapComments() {
        if (!commentEnabled) return
        val duration = lastDurationMs
        // 片长未知（首个快照还没来）：评论先存着，等快照
        if (duration <= 0L) return
        val keys = rawComments.map { it.stableKey }.toSet()
        if (keys == commentKeys && duration == commentDurationMs) return
        commentKeys = keys
        commentDurationMs = duration
        commentItems = CommentDanmakuMapper.map(rawComments, duration)
        remerge()
    }

    companion object {
        private const val TAG = "Danmaku"
    }
}

/**
 * 双源合并（纯函数，单测见 DanmakuMergeTest）。
 *
 * 引擎的发射扫描是"游标 + 二分"，输入必须按 `(playTimeMillis, id)` 升序 ——
 * 排序由这里保证，各源实现不再各自负责。
 */
internal fun mergeDanmakuItems(
    dandanItems: List<DanmakuItem>,
    commentItems: List<DanmakuItem>,
    visible: Set<DanmakuLocation>,
): List<DanmakuItem> = (dandanItems + commentItems)
    .filter { it.location in visible }
    .sortedWith(compareBy({ it.playTimeMillis }, { it.id }))

/**
 * 状态优先级判定（纯函数，单测见 DanmakuStatusTest）。
 *
 * 有关联就说关联（评论数一并报），没关联但有评论就说评论，
 * 都没有才说没关联/不可用。
 */
internal fun resolveDanmakuStatus(
    enabled: Boolean,
    episode: DanmakuEpisodeRef?,
    dandanCount: Int,
    commentCount: Int,
    dandanFailed: Boolean,
    loadAttempted: Boolean,
    jobActive: Boolean,
): DanmakuStatus {
    if (!enabled) return DanmakuStatus.Disabled
    return when {
        episode != null && (dandanCount > 0 || commentCount > 0) ->
            DanmakuStatus.Linked(episode, dandanCount, commentCount)
        episode != null -> DanmakuStatus.NoDanmaku
        commentCount > 0 -> DanmakuStatus.CommentOnly(commentCount)
        dandanFailed -> DanmakuStatus.Unavailable
        loadAttempted && !jobActive -> DanmakuStatus.Unmatched
        else -> DanmakuStatus.Loading
    }
}
