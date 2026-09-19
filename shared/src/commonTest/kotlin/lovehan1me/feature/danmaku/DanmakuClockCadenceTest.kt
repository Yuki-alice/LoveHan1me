package lovehan1me.feature.danmaku

import kotlin.test.Test
import kotlin.test.assertTrue
import lovehan1me.data.danmaku.DanmakuEpisodeRef
import lovehan1me.data.danmaku.DanmakuItem
import lovehan1me.data.danmaku.DanmakuLoadResult
import lovehan1me.data.danmaku.DanmakuLocation

/**
 * 时钟节拍：**测试喂快照的频率必须与生产一致**，否则最要紧的那条路径从未被走过。
 *
 * 生产是「引擎每 250ms(Android) / 500ms(iOS) / 事件驱动(桌面) 推一次位置」，
 * 而 `DanmakuLayer` 每帧（~16ms）问一次位置 —— 中间那段全靠 [DanmakuPositionTracker]
 * 外推。渲染回归用例是**每帧都喂一条快照**，等于把采样抖动设成了 0，
 * 于是"采样间隔 > 外推上限""迟到窗口 vs 时间跳变"这两类只在真机上出现的失效全被绕过。
 */
class DanmakuClockCadenceTest {

    private val episode = DanmakuEpisodeRef(episodeId = "42", episodeTitle = "第 1 话")

    /** 每 400ms 一条，共 10 条，覆盖 0..3.6s。 */
    private fun items(): List<DanmakuItem> = (0 until 10).map { index ->
        DanmakuItem(
            id = index + 1L,
            playTimeMillis = index * 400L,
            text = "弹幕文本$index",
            color = 0xFFFFFFFF.toInt(),
            location = DanmakuLocation.SCROLL,
        )
    }

    private val viewport = DanmakuViewport(widthPx = 1_000f, heightPx = 300f, lineHeightPx = 30f)
    private val measure: (DanmakuItem) -> Float = { it.text.length * 10f }

    private fun session(): DanmakuSession {
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined,
        )
        return DanmakuSession(
            videoCode = "vid-1",
            title = "某番",
            provider = StubProvider,
            scope = scope,
            resolve = { _, _, _ -> DanmakuLoadResult.Ready(episode, items()) },
            linkTo = { _, _, _ -> DanmakuLoadResult.Ready(episode, items()) },
            unlinkFrom = { },
        )
    }

    private object StubProvider : lovehan1me.data.danmaku.DanmakuProvider {
        override val id: String = "stub"
        override suspend fun autoMatch(rawTitle: String): DanmakuEpisodeRef? = null
        override suspend fun searchSubjects(keyword: String): List<lovehan1me.data.danmaku.DanmakuSubject> = emptyList()
        override suspend fun searchEpisodes(subjectTitle: String): List<DanmakuEpisodeRef> = emptyList()
        override suspend fun fetch(episodeId: String): List<DanmakuItem> = emptyList()
    }

    /**
     * 按 [sampleIntervalMs] 推位置、按 [frameIntervalMs] 走帧，跑 [wallMs] 那么长。
     *
     * 暂停的建模要点：**位置在冻结期间不走**（引擎就是原地回显同一个 position），
     * 只有墙钟在走 —— 否则测出来的是"seek"，不是"暂停"。
     *
     * @return 屏上曾出现过的弹幕条数
     */
    private fun run(
        session: DanmakuSession,
        wallMs: Long,
        sampleIntervalMs: Long,
        frameIntervalMs: Long = 16L,
        frozenFromMs: Long? = null,
        frozenToMs: Long? = null,
    ): Int {
        var frameNanos = 0L
        var lastSampleMs = -sampleIntervalMs
        var positionMs = 0L
        var wallTimeMs = 0L
        while (wallTimeMs <= wallMs) {
            val frozen = frozenFromMs != null && wallTimeMs >= frozenFromMs &&
                (frozenToMs == null || wallTimeMs < frozenToMs)
            if (wallTimeMs - lastSampleMs >= sampleIntervalMs) {
                lastSampleMs = wallTimeMs
                session.onPlaybackSnapshot(
                    positionMs = positionMs,
                    durationMs = 1_500_000L,
                    playbackSpeed = 1f,
                    frozen = frozen,
                )
            }
            if (!frozen) positionMs += frameIntervalMs
            frameNanos += frameIntervalMs * DanmakuPositionTracker.NANOS_PER_MILLI
            session.advance(frameNanos, viewport, measure)
            wallTimeMs += frameIntervalMs
        }
        return session.engine.emittedCount
    }

    @Test
    fun `Android 节拍 每250ms一次采样 弹幕全部上场`() {
        val emitted = run(session(), wallMs = 5_000L, sampleIntervalMs = 250L)
        assertTrue(emitted >= 10, "5 秒里只上场了 $emitted/10 条（采样间隔 250ms）")
    }

    @Test
    fun `iOS 节拍 每500ms一次采样 弹幕全部上场`() {
        val emitted = run(session(), wallMs = 5_000L, sampleIntervalMs = 500L)
        assertTrue(emitted >= 10, "5 秒里只上场了 $emitted/10 条（采样间隔 500ms）")
    }

    @Test
    fun `桌面事件驱动 一秒才推一次位置 弹幕也要全部上场`() {
        val emitted = run(session(), wallMs = 5_000L, sampleIntervalMs = 1_000L)
        assertTrue(emitted >= 10, "5 秒里只上场了 $emitted/10 条（采样间隔 1s）")
    }

    @Test
    fun `暂停三秒再恢复 冻结期间到期的弹幕不该整批丢掉`() {
        val emitted = run(
            session(),
            wallMs = 8_000L,
            sampleIntervalMs = 250L,
            frozenFromMs = 1_000L,
            frozenToMs = 4_000L,
        )
        assertTrue(emitted >= 10, "暂停后只上场了 $emitted/10 条：冻结期间到期的弹幕被当成迟到了")
    }

    @Test
    fun `采样恰好落在弹幕时刻之前 也不能把它挤掉`() {
        // 1.2s 采样：0.4s 与 0.8s 两条都落在"上一次采样~下一次采样"之间，
        // 只能靠外推覆盖。外推若被上限截住，这两条就永远追不上。
        val emitted = run(session(), wallMs = 5_000L, sampleIntervalMs = 1_200L)
        assertTrue(emitted >= 10, "5 秒里只上场了 $emitted/10 条（采样间隔 1.2s > 外推上限）")
    }
}
