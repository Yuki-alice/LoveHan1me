package lovehan1me.data.danmaku

import io.ktor.client.HttpClient
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.util.LogUtil
import lovehan1me.data.network.createPlainHttpClient

/**
 * 数据源无关的"一集"标识。
 *
 * id 用 String 而不是 Long：弹幕源的 id 体系各不相同（弹弹play 是数字、
 * 未来可能的自建池是 UUID），仓库侧的 mapping / 缓存表都以字符串为键，
 * 换源时不必动 schema。源内解析不出数字一律按"取不到弹幕"处理。
 */
data class DanmakuEpisodeRef(
    val episodeId: String,
    val episodeTitle: String,
    val subjectId: String = "",
    val subjectTitle: String = "",
)

/** 人工检索时的番剧候选。 */
data class DanmakuSubject(
    val subjectId: String,
    val title: String,
    val episodeCount: Int = 0,
)

/**
 * 弹幕数据源。
 *
 * 接口形状按"能长出第二个源"设计：自动匹配、人工检索、拉取三件事各自独立，
 * 任何源做不到其中一件就抛/返回空，不影响其它路径。
 */
interface DanmakuProvider {

    val id: String

    /**
     * 由标题猜一集。**只在唯一高置信时返回非空，有任何歧义一律返回 null**。
     *
     * 这条约束是功能能否被信任的关键：站内没有 bgm.tv subject id 可作锚点，
     * 猜错的代价（飘一堆不相干的弹幕、还被记进关联表）远高于不猜。
     *
     * 网络/接口异常**向上抛**，别在实现里吞成 null —— 那会让"没网"和"没猜中"
     * 长得一样，仓库侧也就无法回退到过期缓存。
     */
    suspend fun autoMatch(rawTitle: String): DanmakuEpisodeRef?

    suspend fun searchSubjects(keyword: String): List<DanmakuSubject>

    suspend fun searchEpisodes(subjectTitle: String): List<DanmakuEpisodeRef>

    /**
     * 拉一集的弹幕。**返回必须按 [DanmakuItem.playTimeMillis] 升序** ——
     * 弹幕引擎的发射扫描是"游标 + 二分"，无序输入会让它漏放或倒放。
     * 排序由各实现自己负责（缓存侧的 `ORDER BY` 覆盖不到首次拉取这条路）。
     */
    suspend fun fetch(episodeId: String): List<DanmakuItem>

    companion object {
        /**
         * 第三方 API 专用客户端：不带站点 Cookie / CSRF / CF / 代理选择器那条链。
         *
         * 只构造一次 —— [createPlainHttpClient] 每调一次就是一个新引擎，
         * 每次进播放器都建一个会把连接池打爆。
         */
        private val plainClient: HttpClient by lazy { createPlainHttpClient() }

        /**
         * 按设置装配数据源。**返回 null 表示功能休眠**：调用方据此连 session 都不构造，
         * 自然也不会有任何网络请求与绘制层。
         *
         * 刻意**不看** [AppSettings.danmakuEnabled]：那是用户开关，与"有没有配置"
         * 是两件事 —— 播放器状态条需要区分「未配置·去设置」与「已配置·已关闭」，
         * 混成一个 null 就撒不了谎了。
         */
        fun from(settings: AppSettings): DanmakuProvider? {
            val proxy = settings.danmakuProxyBase.trim().asValidBaseUrl()
            if (proxy == null && settings.danmakuProxyBase.isNotBlank()) {
                // 用户明明填了却静默忽略，是最难自查的一类 bug
                LogUtil.w("弹幕代理地址被忽略（仅支持 http/https）: ${settings.danmakuProxyBase}")
            }
            val appId = settings.danmakuAppId.trim()
            val appSecret = settings.danmakuAppSecret.trim()
            val credentials =
                if (appId.isNotEmpty() && appSecret.isNotEmpty()) DandanCredentials(appId, appSecret)
                else null
            if (appId.isEmpty() != appSecret.isEmpty()) {
                LogUtil.w("弹幕 AppID 与 AppSecret 需成对填写，当前按未配置处理")
            }
            // 官方端点没有匿名读接口（实测 errorCode=3「应用不存在」），
            // 所以既没代理又没凭据时直连官方毫无意义 —— 判为未配置。
            if (proxy == null && credentials == null) return null
            return DandanplayProvider(
                api = DandanplayApi(
                    client = plainClient,
                    baseUrl = proxy ?: DandanplayApi.BASE_URL,
                    credentials = credentials,
                ),
            )
        }
    }
}

/**
 * 校验并归一化用户填的代理地址：只接受 http/https，去掉尾斜杠。
 *
 * 不做白名单是有意的 —— 代理地址本来就是用户自运维的东西，写死域名等于替用户决定
 * 谁能看到他的请求。但也因此必须挡掉 `file://`、`javascript:` 这类形状，
 * 它会被当作 URL 直接喂给 HTTP 引擎。
 */
internal fun String.asValidBaseUrl(): String? {
    val trimmed = trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) return null
    return trimmed
}

/** 弹弹play 实现的对外门面（把 DTO 翻译成源无关类型，并落实"高置信才自动匹配"）。 */
internal class DandanplayProvider(
    private val api: DandanplayApi,
) : DanmakuProvider {

    override val id: String = PROVIDER_ID

    override suspend fun autoMatch(rawTitle: String): DanmakuEpisodeRef? {
        val cleaned = DandanplayMatcher.cleanTitle(rawTitle)
        if (cleaned.isBlank()) return null
        // 网络异常直接向上抛：由 DanmakuRepository 统一降级为 Unmatched，
        // 在这里吞掉会把协程取消也变成"没匹配上"。
        val episodeNumber = DandanplayMatcher.parseEpisodeNumber(rawTitle)

        // 第一阶段：`search/anime` 番剧检索 + 集号对齐。
        //
        // 逐个检索词试到**对上集号**为止（站内标题常带 `OVA` 这类库里没有的前缀）。
        // 打分永远用原 `cleaned`：备选词只是把候选捞回来，不能替它自证匹配。
        //
        // 有集号时不再要求"标题唯一最高分"：`OVA` vs `剧场版` 同分是常态，
        // 集号对齐本身才是最硬的信号 —— 哪个候选含这一集，哪个就是答案。
        // 无集号时退回唯一高置信（标题一锤定音，猜错即污染）。
        for (query in DandanplayMatcher.searchQueries(cleaned)) {
            val candidates = api.searchAnime(query)
            if (episodeNumber != null) {
                val ranked = DandanplayMatcher.rankSubjects(candidates, cleaned)
                for ((subject, _) in ranked.take(MAX_SUBJECT_TRIES)) {
                    val episode = searchAlignedEpisode(subject, episodeNumber) ?: continue
                    return episode
                }
                if (ranked.isNotEmpty()) continue
            } else {
                val subject = DandanplayMatcher.pickSubject(candidates, cleaned) ?: continue
                val grouped = api.searchEpisodes(subject.animeTitle)
                    .firstOrNull { it.animeId == subject.animeId }?.episodes.orEmpty()
                DandanplayMatcher.pickEpisode(grouped, null)?.let { episode ->
                    return DanmakuEpisodeRef(
                        episodeId = episode.episodeId.toString(),
                        episodeTitle = episode.episodeTitle,
                        subjectId = subject.animeId.toString(),
                        subjectTitle = subject.animeTitle,
                    )
                }
            }
        }

        // 第二阶段：`search/episodes` 直搜兜底（见 `pickAlignedEpisode`）。
        // 上一阶段是"先定番剧再找集"，这里是"连番带集一起捞、本地再裁决"。
        // 短查询（最长分词）只在这里追加：上一阶段的 `searchQueries` 已经试过一次，
        // 全标题在另一条索引下仍值得再试（两条索引的命中集合不一样）。
        val fallbackQueries = buildList {
            add(cleaned)
            DandanplayMatcher.longestToken(cleaned)?.let { add(it) }
        }
        for (query in fallbackQueries) {
            val groups = api.searchEpisodes(query)
            DandanplayMatcher.pickAlignedEpisode(groups, cleaned, episodeNumber)?.let { return it }
        }
        return null
    }

    /**
     * 在该番剧名下按 animeId 认领分组并做集号对齐。
     *
     * 按 animeId 认领：标题相同而季度不同的另一部番剧的集数不能混进来。
     * 集号对齐：站内的 `＃5` / `第5话` / `第五話` 对库里的 `第5话 xxx`。
     * 解析不出集号、或同号多条/零条，都不猜 —— 猜错等于把另一集的时间轴盖上来。
     */
    private suspend fun searchAlignedEpisode(
        subject: DandanAnime,
        episodeNumber: Int,
    ): DanmakuEpisodeRef? {
        val grouped = api.searchEpisodes(subject.animeTitle)
            // 按 animeId 认领分组：标题相同而季度不同的另一部番剧的集数不能混进来
            .firstOrNull { it.animeId == subject.animeId }?.episodes.orEmpty()
        return DandanplayMatcher.pickEpisode(grouped, episodeNumber)?.let { episode ->
            DanmakuEpisodeRef(
                episodeId = episode.episodeId.toString(),
                episodeTitle = episode.episodeTitle,
                subjectId = subject.animeId.toString(),
                subjectTitle = subject.animeTitle,
            )
        }
    }

    override suspend fun searchSubjects(keyword: String): List<DanmakuSubject> =
        api.searchAnime(keyword).map {
            DanmakuSubject(
                subjectId = it.animeId.toString(),
                title = it.animeTitle,
                episodeCount = it.episodeCount,
            )
        }

    override suspend fun searchEpisodes(subjectTitle: String): List<DanmakuEpisodeRef> =
        api.searchEpisodes(subjectTitle).flatMap { subject ->
            // 分组摊平，但归属跟着各自的父番剧走，不拿查询词冒充标题
            subject.episodes.map {
                DanmakuEpisodeRef(
                    episodeId = it.episodeId.toString(),
                    episodeTitle = it.episodeTitle,
                    subjectId = subject.animeId.toString(),
                    subjectTitle = subject.animeTitle,
                )
            }
        }

    override suspend fun fetch(episodeId: String): List<DanmakuItem> {
        val numeric = episodeId.toLongOrNull() ?: return emptyList()
        // 排序是下游引擎的前提（游标 + 二分要求 playTimeMillis 升序），
        // 而接口并不保证 comments 有序，所以必须在这里落实，别指望缓存侧的 ORDER BY
        // 覆盖首次拉取那条路径。
        return api.getComments(numeric).mapNotNull { it.toItem() }.sortedBy { it.playTimeMillis }
    }

}

/** 弹弹未配置时的空桩：评论主源照常工作，弹弹那路恒落空（选集弹窗搜出空结果）。 */
internal object EmptyDanmakuProvider : DanmakuProvider {
    override val id: String = "none"
    override suspend fun autoMatch(rawTitle: String): DanmakuEpisodeRef? = null
    override suspend fun searchSubjects(keyword: String): List<DanmakuSubject> = emptyList()
    override suspend fun searchEpisodes(subjectTitle: String): List<DanmakuEpisodeRef> = emptyList()
    override suspend fun fetch(episodeId: String): List<DanmakuItem> = emptyList()
}

private const val PROVIDER_ID = "dandanplay"

/**
 * 单个检索词下最多试对几个番剧候选：标题分掉到第 4 名基本是同分陪跑，
 * 对上也是撞大运；且每个候选都是一次 `search/episodes` 往返，封顶控延迟。
 */
private const val MAX_SUBJECT_TRIES = 3
