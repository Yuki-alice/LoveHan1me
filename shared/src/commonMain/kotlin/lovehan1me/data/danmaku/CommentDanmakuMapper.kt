package lovehan1me.data.danmaku

import lovehan1me.core.domain.model.VideoComments

/**
 * 站内评论 → 弹幕（主数据源）。
 *
 * 弹弹play 是 Bangumi 体系，里番命中率极低且额度记在共享 AppId 上；
 * 而评论随视频页本来就要拉一次（`CommentViewModel.getComment`），边际成本为 0、
 * 与任何第三方额度无关。翻转后的分工：评论是**永远有的主源**，
 * 弹弹是**有时有、时间轴精确的增强层**，两路在 [lovehan1me.feature.danmaku.DanmakuSession]
 * 里合并后一次性喂给引擎。
 *
 * ## 时间是"排"出来的，不是"对"出来的
 * 评论没有时间字段，做法是严格均匀散布：按点赞排序，沿可用片长等距排开
 * （高赞在前，开场即热闹），间隔 = 可用片长 / 评论数，落点再叠一层
 * ±10% 步长的确定性抖动（由 `stableKey` 哈希决定——同 key 同位置，
 * 且相邻两条不完全同步，免得同帧同 x 出发）。
 * 别指望它和剧情对上：散布弹幕的定位就是"有人陪着看"，精确对齐是弹弹那路的事。
 *
 * ## 数量不限（子评论除外）
 * 除子评论（另有 `loadReplies` 接口，一个 N+1 灾难）外，能拉到多少投多少：
 * 引擎发射是游标 + 二分（每 tick 只消费到期前缀），屏上条数被车道数封顶，
 * 几百条撒到十几分钟里每 tick 到期 0~2 条，测量缓存也 cover 得住。
 */
object CommentDanmakuMapper {

    /** 单条截断：评论是段落体，40 字以上在滚动轨道上就是一条横幅。 */
    const val MAX_TEXT_CHARS = 40

    /** 片头片尾留白：开场留标题与缓冲，结尾 `Ended` 画面不飘字。 */
    const val HEAD_MARGIN_MS = 5_000L
    const val TAIL_MARGIN_MS = 10_000L

    /** 短于此时长不投影： intervals 全挤在一起，不如不撒。 */
    const val MIN_DURATION_MS = 30_000L

    /**
     * 评论充足到这个数就**跳过弹弹自动匹配**（手动关联不受影响）。
     *
     * 热门视频本来评论就够热，没必要再花共享额度去碰运气；
     * 冷门视频评论凑不满，额度才花得值。判据只看映射后的条数，
     * 与"有没有配置弹弹"无关。
     */
    const val COMMENT_ABUNDANT_THRESHOLD = 40

    /**
     * @param durationMs 片长（引擎快照里的 `durationMs`，≤0 表示还没拿到，先返回空）
     * @return 按 `playTimeMillis` 升序（引擎游标 + 二分的前置条件，调用方不再排）
     */
    fun map(
        comments: List<VideoComments.VideoComment>,
        durationMs: Long,
    ): List<DanmakuItem> {
        if (comments.isEmpty() || durationMs < MIN_DURATION_MS) return emptyList()
        val ranked = comments
            .filter { !it.isChildComment && it.content.isNotBlank() }
            .sortedWith(
                compareByDescending<VideoComments.VideoComment> { it.thumbUp ?: 0 }
                    .thenBy { it.stableKey },
            )
        if (ranked.isEmpty()) return emptyList()

        val start = minOf(HEAD_MARGIN_MS, durationMs / 10)
        val end = maxOf(start + 1_000L, durationMs - minOf(TAIL_MARGIN_MS, durationMs / 10))
        val span = (end - start).coerceAtLeast(1L)
        val step = span.toDouble() / ranked.size

        return ranked.mapIndexed { index, comment ->
            val hash = comment.stableKey.hashCode() and Int.MAX_VALUE
            // ±10% 步长的抖动：确定性（同 key 同位置），相邻间隔恒在 [0.8, 1.2] 步长内
            val jitter = (hash % 1000) / 1000.0 * 0.2 - 0.1
            val timeMs = (start + step * (index + 0.5 + jitter)).toLong()
                .coerceIn(start, end)
            // 负数 id：远端 cid 从 1 起，不可能撞；同哈希撞了也无妨（引擎不要求 id 唯一）
            val rawId = comment.stableKey.hashCode().toLong() and 0x7FFFFFFFL
            DanmakuItem(
                id = if (rawId == 0L) -1L else -rawId,
                playTimeMillis = timeMs,
                text = truncate(comment.content.trim().collapseWhitespace()),
                color = 0xFFFFFFFF.toInt(),
                location = DanmakuLocation.SCROLL,
                source = DanmakuSource.COMMENT,
            )
        }.sortedWith(compareBy({ it.playTimeMillis }, { it.id }))
    }

    private fun String.collapseWhitespace(): String =
        trim().replace(whitespaceRegex, " ")

    private fun truncate(text: String): String {
        if (text.length <= MAX_TEXT_CHARS) return text
        return text.take(MAX_TEXT_CHARS).trimEnd() + "…"
    }

    private val whitespaceRegex = Regex("\\s+")
}
