package lovehan1me.data.danmaku

/**
 * 引擎层弹幕模型（与数据源无关：弹弹play、本地自发弹幕都映射到这里）。
 */
data class DanmakuItem(
    /** 稳定 id（弹弹 cid；本地自发用负数时间戳，保证与远端不撞）。 */
    val id: Long,
    /** 出现时刻（毫秒，相对片头）。 */
    val playTimeMillis: Long,
    val text: String,
    /** ARGB 整型（含 alpha）。 */
    val color: Int,
    val location: DanmakuLocation,
    /** 是否自己发的（自发弹幕描边高亮用）。 */
    val isSelf: Boolean = false,
    /** 哪来的：远端时间轴弹幕 vs 站内评论投影（状态条计数与将来按源过滤用）。 */
    val source: DanmakuSource = DanmakuSource.REMOTE,
)

enum class DanmakuSource {
    /** 弹弹play：时间轴精确。 */
    REMOTE,

    /** 站内评论投影：时间是排出来的，主源但精度低。 */
    COMMENT,
}

enum class DanmakuLocation {
    SCROLL,
    TOP,
    BOTTOM,
}

/**
 * 弹弹play 标题匹配用的纯函数集合（清洗 → 归一化 → 打分 → 挑番挑集）。
 *
 * ## 判据一律"宁可不猜"
 * 错关联比不关联坏得多：不关联只是没弹幕，错关联是把**另一集的时间轴**盖到这部片子上。
 * 实测过一例 —— 站内 `OVA催眠性指導 ＃5` 被人工选到 `第1话` 时，全片只有 45 条、
 * 而 8:47 那个位置之后只剩 10 条、下一条还要再等 28 秒，用户的结论就是"根本没实现"。
 * 所以自动那条路只在唯一高置信时落库，任何一步含糊都返回 null，调用方不报错、
 * 不 toast、不重试（命中是惊喜，落空是常态，主路径仍是人工选集 + 记住关联）。
 *
 * ## 为什么不"清洗严格一点就能命中"
 * 里番标题在弹弹库里基本命中不了（那是 Bangumi 体系）。但**表番也不是一定命中**：
 * `催眠性指導`（站内日文旧字）对 `催眠性指导`（库里简体）在字面上互不包含，
 * 而官方检索自身会做繁简/异体归一，只回一条结果 —— 所以"唯一命中"本身就是证据，
 * 见 [pickSubject]。
 */
/** 标题打分后的番剧候选：自动匹配按此顺序逐个试集号对齐，先对上先采纳。 */
data class ScoredSubject(
    val subject: DandanAnime,
    val score: Int,
)

object DandanplayMatcher {

    /**
     * 检索用清洗：去掉**任意位置**的 `[...]` `(...)` `【...】` 段（社团、字幕组、
     * `[中文字幕]` 这类尾部标记同样要扔 —— 站内标题十有八九把它放末尾）、
     * 多标题并列时取第一段、**中段集号标记及之后全切**、尾部明确的集号标记切掉。
     *
     * 中段一切是必需的：站内标题常是 `作品 第7話 本话副标题` 形
     * （如 `小女ラムネ 第7話 コマコとエッチなお約束`），集号后面还拖着副标题，
     * 不切的话整串拿去检索必是 0 条。切完只剩作品名，集号由 [parseEpisodeNumber]
     * 另从原标题解析 —— "作品名 + 集号"两件事本来就该分开。
     *
     * 只认**带集单位**的 `第N話` 形：`第3期` / `第2季` 是季度信息，切掉会把
     * 季度对齐搞丢（`夏目友人帐第3期` 切成 `夏目友人帐` 再对上第 N 集，
     * 对上的可能是第一季的第 N 集）。标记在开头（`第5話 作品名`）则不动 ——
     * 前面没有作品名可取，乱切等于把标题清空。
     *
     * 例：`[Keke Animations] Columbina & Sandrone #48` → `Columbina & Sandrone`；
     * `OVA催眠性指導 ＃5 [中文字幕]` → `OVA催眠性指導`；
     * `小女ラムネ 第7話 コマコとエッチなお約束 [中文字幕]` → `小女ラムネ`。
     */
    fun cleanTitle(raw: String): String {
        // 全角先折半角：`＃５` 的尾部标记切不掉、搜出来也对不上库里的半角建档
        val stripped = stripBracketSegments(raw).foldSearchWidth()
        val firstSegment = stripped.split('|', '/').firstOrNull()?.trim().orEmpty()
        val beforeMarker = midEpisodeMarkerRegex.find(firstSegment)
            ?.takeIf { it.range.first > 0 }
            ?.let { firstSegment.substring(0, it.range.first).trim() }
        val workName = if (!beforeMarker.isNullOrEmpty()) beforeMarker else firstSegment
        val withoutMarker = trailingEpisodeMarkerRegex.find(workName)
            ?.let { workName.removeSuffix(it.value).trim() }
            .orEmpty()
        return withoutMarker.ifBlank { workName.ifBlank { stripped.trim() } }
    }

    /**
     * 依次尝试的检索词：清洗后的标题，外加"去掉开头类型前缀"的备选。
     *
     * 实测依据（弹弹play 线上）：站内 `OVA催眠性指導` 直接搜 **0 条**，
     * 去掉 `OVA` 后搜到 1 条 `催眠性指导`。前缀是字幕组/站内的写法差异，
     * 库里却按干净标题建档。
     *
     * 注意：**打分仍然用原清洗结果**（见 [DandanplayProvider.autoMatch]），备选词只负责
     * "把候选捞回来"，不负责"证明匹配" —— 捞回来的名字必须仍然像原查询才算命中。
     *
     * 第三顺位再给一个"短查询"：尾部噪音词（`特典` / `剧场版` / 裸年份 `2019` 这类，
     * animeko 在模糊检索里同样会砍掉最后一个词）实在没得砍时，退到最长有效分词
     * （`某动画 2019` → `某动画`）。官方 `search/anime` 是关键词检索，
     * 全标题多一个词就可能从有到无，短查询是召回的底线。
     *
     * 列表恒 ≤3：自动匹配跑在进播放器的关键路径上，每多一个查询就是一次往返。
     */
    fun searchQueries(cleaned: String): List<String> {
        val queries = mutableListOf(cleaned)
        val withoutTypePrefix = leadingTypePrefixRegex.replace(cleaned, "").trim()
        if (withoutTypePrefix.isNotEmpty() && withoutTypePrefix != cleaned) queries += withoutTypePrefix
        val withoutJunkTail = trailingJunkTokenRegex.replace(cleaned, "").trim()
        if (withoutJunkTail.length >= MIN_QUERY_CHARS && withoutJunkTail != cleaned &&
            withoutJunkTail !in queries
        ) {
            queries += withoutJunkTail
        } else {
            longestToken(cleaned)?.let { token ->
                if (token !in queries) queries += token
            }
        }
        return queries
    }

    /**
     * 最长有效分词：按空白与常见分隔符切开，取最长的 ≥2 字词。
     *
     * 找不到（单字标题、纯符号）返回 null —— 调用方届时本来也只有全标题可用。
     */
    internal fun longestToken(cleaned: String): String? {
        var best: String? = null
        for (token in cleaned.split(tokenSeparatorRegex)) {
            if (token.length < MIN_QUERY_CHARS || token == cleaned) continue
            // 集号残留与纯数字（`第5話` / `2019`）当查询只会捞回垃圾：跳过
            if (token.all { it.isDigit() } || episodeTokenRegex.matches(token)) continue
            if (best == null || token.length > best.length) best = token
        }
        // 首个最长胜出：并列时前面的词通常是作品名，后面的是副标题/集号残留
        return best
    }

    /**
     * 比较用归一化：全角 ASCII → 半角、统一小写、丢掉一切非字母数字（空白与标点）。
     *
     * 只做这三件事，**不做繁简转换** —— 那是几十万字形的表，而真需要它的地方
     * （`指導` vs `指导`）已经由官方检索替我们归一了。
     */
    fun normalizeForCompare(title: String): String {
        val out = StringBuilder(title.length)
        for (char in title) {
            val folded = when (char) {
                in '\uFF01'..'\uFF5E' -> (char.code - 0xFEE0).toChar()
                '\u3000' -> ' '
                else -> char
            }.lowercaseChar()
            if (folded.isLetterOrDigit()) out.append(folded)
        }
        return out.toString()
    }

    /**
     * 从标题里解析集号：`＃5` / `#5` / `第5话` / `第5話` / `第五話`（汉字数字）/
     * `第1巻` / `EP5` / `Vol.2` / `- 05` / 结尾裸数字（`Columbina & Sandrone 48`）。
     * 解析不出返回 null。
     *
     * 全角数字先折成半角再匹配：站内标题的 `＃５` 是全角 5，`\d` 吃不下它，
     * 不折的话这一整类直接判"无集号"，多集番剧于是永远走"不猜"。
     *
     * 裸数字只认**结尾 1~2 位**，所以 `Re:0`、`2019` 这类不会被当成第 0 / 2019 集。
     */
    fun parseEpisodeNumber(raw: String): Int? {
        val text = raw.trim().foldSearchWidth()
        for (pattern in episodeNumberRegexes) {
            pattern.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { number ->
                if (number > 0) return number
            }
        }
        kanjiEpisodeNumberRegex.find(text)?.groupValues?.getOrNull(1)
            ?.let(::parseKanjiNumber)?.let { number ->
                if (number > 0) return number
            }
        return null
    }

    /**
     * 汉字数字 → Int，只到 100（`十` / `十二` / `二十五` / `一百`）。
     *
     * 超出形状（`二五` 这类歧义写法、含其它字符）返回 null，调用方继续试下一条模式。
     */
    internal fun parseKanjiNumber(raw: String): Int? {
        if (raw == "一百") return 100
        if (raw.isEmpty() || raw.length > 3) return null
        val digit = { char: Char -> kanjiDigits[char] }
        val tenIndex = raw.indexOf('十')
        if (tenIndex < 0) return raw.singleOrNull()?.let(digit)
        val tens = if (tenIndex == 0) 1
        else raw.substring(0, tenIndex).singleOrNull()?.let(digit) ?: return null
        val ones = if (tenIndex == raw.lastIndex) 0
        else raw.substring(tenIndex + 1).singleOrNull()?.let(digit) ?: return null
        return tens * 10 + ones
    }

    /**
     * 全角折半角：全角空格 → 空格、全角数字 → 半角。
     *
     * 集号解析与标题清洗共用：`＃５` 的尾部标记不折就切不掉，搜出来也对不上
     * 库里的半角建档。只动这两类字符，其它全角（假名、汉字）原样保留。
     */
    private fun String.foldSearchWidth(): String {
        if (none { it == '\u3000' || it in '\uFF10'..'\uFF19' }) return this
        return map { char ->
            when (char) {
                '\u3000' -> ' '
                in '\uFF10'..'\uFF19' -> '0' + (char - '\uFF10')
                else -> char
            }
        }.joinToString("")
    }

    /**
     * 从候选番剧里挑出**唯一高置信**的那一个，有歧义返回 null（交人工选集）。
     *
     * 这是"无集号可用"时的守门员：调用方拿不到集号，就只能靠标题本身一锤定音。
     * 有集号时请走排名试对（见 `rankSubjects` + 逐番剧集号对齐），让集号替并列的
     * 候选裁决 —— `催眠性指导 OVA` vs `催眠性指导 剧场版` 同分时，
     * 只有含第 5 话的那部才是答案，标题分本身裁决不了。
     *
     * @param query 已经过 [cleanTitle] 的站内标题
     */
    fun pickSubject(candidates: List<DandanAnime>, query: String): DandanAnime? {
        if (candidates.isEmpty()) return null
        val ranked = rankSubjects(candidates, query)
        if (ranked.isEmpty()) return null
        val best = ranked.first().score
        val winners = ranked.filter { it.score == best }
        return when {
            best >= MIN_CONFIDENT_EXACT_SCORE && winners.size == 1 -> winners.first().subject
            // 只有一个候选时不必自负：能进候选列表就说明官方检索按关键字把它筛出来了。
            candidates.size == 1 && best >= MIN_CONFIDENT_SINGLE_SCORE -> candidates.first()
            else -> null
        }
    }

    /** 候选按标题分从高到低排好（0 分的垃圾先滤掉）：自动匹配逐个试对、逐个做集号对齐。 */
    fun rankSubjects(candidates: List<DandanAnime>, query: String): List<ScoredSubject> =
        candidates.map { ScoredSubject(it, scoreSubject(query, it.animeTitle)) }
            .filter { it.score >= MIN_CONFIDENT_SINGLE_SCORE }
            .sortedByDescending { it.score }

    /**
     * 在 `search/episodes` 的分组结果里直接挑一集（自动匹配第二阶段，不经过 `search/anime`）。
     *
     * 存在的理由：`search/anime` 按关键词检索**只回 25 条**（Kazumi 为柯南 48 条目
     * 踩过这个坑），热门系列的主番会被截掉；而 `search/episodes` 走另一条索引，
     * 上一阶段搜不到不等于这里也没有。
     *
     * 判据与第一阶段同构：标题分过门槛 + 集号精确对齐；有集号时允许它替并列的
     * 番剧裁决（单组对上即采纳），无集号时退回"唯一高置信组且组内仅一集"。
     * 打分永远用原 `query`（调用方传清洗后的站内标题），分组是怎么捞回来的不重要。
     */
    fun pickAlignedEpisode(
        groups: List<DandanSubjectEpisodes>,
        query: String,
        episodeNumber: Int?,
    ): DanmakuEpisodeRef? {
        if (groups.isEmpty()) return null
        val scored = groups
            .map { group -> group to scoreSubject(query, group.animeTitle) }
            .filter { it.second >= MIN_CONFIDENT_SINGLE_SCORE }
            .sortedByDescending { it.second }
        if (scored.isEmpty()) return null
        if (episodeNumber != null) {
            val aligned = scored.mapNotNull { (group, score) ->
                pickEpisode(group.episodes, episodeNumber)?.let { Triple(group, it, score) }
            }
            if (aligned.isEmpty()) return null
            val best = aligned.maxOf { it.third }
            val winner = aligned.filter { it.third == best }.singleOrNull() ?: return null
            if (winner.third < MIN_CONFIDENT_EXACT_SCORE && scored.size != 1) return null
            return winner.first.toRef(winner.second)
        }
        val (group, score) = scored.singleOrNull() ?: return null
        if (score < MIN_CONFIDENT_EXACT_SCORE && groups.size != 1) return null
        return pickEpisode(group.episodes, null)?.let { group.toRef(it) }
    }

    private fun DandanSubjectEpisodes.toRef(episode: DandanEpisode): DanmakuEpisodeRef =
        DanmakuEpisodeRef(
            episodeId = episode.episodeId.toString(),
            episodeTitle = episode.episodeTitle,
            subjectId = animeId.toString(),
            subjectTitle = animeTitle,
        )

    /**
     * 用集号把剧集对齐。
     *
     * @param episodeNumber 站内标题解析出的集号；null 时只有"整部番就一集"才敢取
     * @return 同号且唯一才算命中；同号多条（上下篇各占一行之类）一律不猜
     */
    fun pickEpisode(episodes: List<DandanEpisode>, episodeNumber: Int?): DandanEpisode? {
        if (episodes.isEmpty()) return null
        if (episodeNumber == null) return episodes.singleOrNull()
        return episodes.filter { parseEpisodeNumber(it.episodeTitle) == episodeNumber }
            .singleOrNull()
    }

    /** 归一化后相等 3 / 互相包含或"近乎同序"2 / 二元组够像 1 / 无关 0。 */
    private fun scoreSubject(query: String, candidateTitle: String): Int {
        val left = normalizeForCompare(query)
        val right = normalizeForCompare(candidateTitle)
        if (left.isEmpty() || right.isEmpty()) return 0
        return when {
            left == right -> 3
            left.contains(right) || right.contains(left) -> 2
            // 繁简/异体字（`指導` vs `指导`）会让"包含"差一个字就彻底落空，
            // 而这类差异**恰好**表现为"整条子序列同序、个别字不同"。
            // 要求公共子序列至少 3 个字：2 个字的巧合太多了。
            isNearSubstring(left, right) -> 2
            characterBigramSimilarity(left, right) >= FUZZY_SIMILARITY -> 1
            else -> 0
        }
    }

    /** 短的那条几乎按原顺序嵌在长的那条里（允许少量字符被替换）。 */
    private fun isNearSubstring(left: String, right: String): Boolean {
        val shorterLength = minOf(left.length, right.length)
        val common = longestCommonSubsequenceLength(left, right)
        return common >= MIN_NEAR_SUBSEQUENCE_CHARS && common * 5 >= shorterLength * 4
    }

    /** LCS 长度：两条标题都在几十字符内，滚动一维数组足够。 */
    private fun longestCommonSubsequenceLength(left: String, right: String): Int {
        var previousRow = IntArray(right.length + 1)
        for (i in left.indices) {
            val currentRow = IntArray(right.length + 1)
            for (j in right.indices) {
                currentRow[j + 1] = if (left[i] == right[j]) {
                    previousRow[j] + 1
                } else {
                    maxOf(previousRow[j + 1], currentRow[j])
                }
            }
            previousRow = currentRow
        }
        return previousRow[right.length]
    }

    /** 字符二元组的 Jaccard 相似度：对"改了个别字/多了后缀"的标题比逐字相等宽容。 */
    internal fun characterBigramSimilarity(left: String, right: String): Float {
        if (left.length < 2 || right.length < 2) return if (left == right) 1f else 0f
        val leftSets = left.bigrams()
        val rightSets = right.bigrams()
        val intersection = leftSets.count { it in rightSets }
        val union = leftSets.size + rightSets.size - intersection
        return if (union == 0) 0f else intersection.toFloat() / union
    }

    private fun String.bigrams(): Set<String> =
        if (length < 2) setOf(this) else (0..length - 2).map { substring(it, it + 2) }.toHashSet()

    /** 去掉任意位置的成对括号段；不成对的右括号按普通字符留着。 */
    private fun stripBracketSegments(input: String): String {
        val out = StringBuilder(input.length)
        var depth = 0
        for (char in input) {
            when (char) {
                '[', '【', '(', '（' -> depth++
                ']', '】', ')', '）' -> if (depth > 0) depth-- else out.append(char)
                else -> if (depth == 0) out.append(char)
            }
        }
        return out.toString()
    }

    private const val MIN_CONFIDENT_EXACT_SCORE = 2
    private const val MIN_CONFIDENT_SINGLE_SCORE = 1
    private const val FUZZY_SIMILARITY = 0.72f
    private const val MIN_NEAR_SUBSEQUENCE_CHARS = 3

    /** 检索词最短长度：单字查询基本只会捞回垃圾，不如不发这次请求。 */
    private const val MIN_QUERY_CHARS = 2

    private val kanjiDigits = mapOf(
        '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
        '六' to 6, '七' to 7, '八' to 8, '九' to 9,
    )

    /** 标题尾部的噪音词：裁掉它再搜一遍（`某番 特典` → `某番`，`某动画 2019` → `某动画`）。 */
    private val trailingJunkTokenRegex = Regex(
        """\s+(?:特典|特别篇|番外篇|OVA|OAD|ONA|SP|TV|剧场版|映画|\d{1,4})\s*$""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 中段集号标记（`作品 第7話 副标题` 里的 `第7話`）：[cleanTitle] 切到它为止。
     *
     * `第` 形必须带集单位（`第3期` 是季度不是集数，见 [cleanTitle]）；
     * `第` 前不加 `\b` —— `\b` 是 ASCII 词界，假名/汉字后面它恒为假，
     * 加了 `ラムネ第7話` 这种就永远切不到；`-N` 形要求破折号前有空白
     * （`K-1` 不切，`作品 - 03` 切 —— 多消费的那个空格由切点 trim 掉），
     * 且全程不用 lookbehind（iOS 的 NSRegularExpression 后端不支持会炸类加载）。
     */
    private val midEpisodeMarkerRegex = Regex(
        """[#＃]\s*\d{1,3}|第\s*(?:\d{1,3}|[一二三四五六七八九十百]+)\s*(?:话|話|集|回|卷|巻)|\b(?:ep|episode|eps|vol)\.?\s*\d{1,3}|\s[-–]\s*\d{1,3}(?!\d)""",
        RegexOption.IGNORE_CASE,
    )

    /** 单个分词是集号残留（`第5話` / `#48` / `EP7`）：[longestToken] 跳过它。 */
    private val episodeTokenRegex = Regex(
        """[#＃]?\s*\d{1,3}|第\s*(?:\d{1,3}|[一二三四五六七八九十百]+)\s*(?:话|話|集|回|卷|巻)?|(?:ep|episode|eps|vol)\.?\s*\d{1,3}""",
        RegexOption.IGNORE_CASE,
    )

    /** 分词用分隔符：空白（含全角）与常见连接符。`&` 不切 —— `A & B` 的两边都太短时整段仍可用。 */
    private val tokenSeparatorRegex = Regex("""[\s\u3000·・･~/～〜\-—_#＃|]+""")

    /**
     * 开头的类型前缀：`OVA催眠性指導` 的 `OVA`。
     *
     * 后面必须不接 ASCII 字母数字（负向前瞻），否则 `TVB…` 这类真名开头的标题会被啃掉三个字母。
     */
    private val leadingTypePrefixRegex = Regex(
        """^(?:OVA|OAD|ONA|TV|SP)(?![0-9A-Za-z])""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 只认"明确标记"的尾部集号，避免把 `Re:0` 这类标题后缀切掉。汉字数字同步支持。
     *
     * `第` 形必须带集单位：`夏目友人帐第3期` 的 `第3期` 是季度不是集数，
     * 切掉它再对上集号，对到的可能是第一季的同一集。裸 `第5` 这种不带单位的
     * 由短查询兜召回（`searchQueries` 的尾部噪音词含裸数字），不在这里切。
     */
    private val trailingEpisodeMarkerRegex = Regex(
        """\s*(?:[#＃]\s*\d{1,3}|第\s*(?:\d{1,3}|[一二三四五六七八九十百]+)\s*(?:话|話|集|回|卷|巻)|(?:eps?\.?|episode|vol\.?)\s*\d{1,3})\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private val episodeNumberRegexes = listOf(
        Regex("[#＃]\\s*(\\d{1,3})"),
        Regex("第\\s*(\\d{1,3})\\s*(?:话|話|集|回|卷|巻)"),
        Regex("""\b(?:ep|episode|eps)\.?\s*(\d{1,3})""", RegexOption.IGNORE_CASE),
        Regex("""\bvol\.?\s*(\d{1,3})""", RegexOption.IGNORE_CASE),
        Regex("""[-–]\s*(\d{1,3})\s*$"""),
        Regex("""\s(\d{1,2})\s*$"""),
    )

    /** 汉字集号（`第五話` / `第十二回`）：捕获后走 [parseKanjiNumber] 换算，单独一条模式。 */
    private val kanjiEpisodeNumberRegex =
        Regex("第\\s*([一二三四五六七八九十百]+)\\s*(?:话|話|集|回|卷|巻)")
}
