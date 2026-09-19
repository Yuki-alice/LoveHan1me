package lovehan1me.data.danmaku

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.util.Sha256
import lovehan1me.data.datastore.danmakuCredentialsToPersist
import lovehan1me.data.datastore.resolveDanmakuCredentials
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Sha256Test {
    @Test
    fun `NIST向量空串`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hex(ByteArray(0)),
        )
    }

    @Test
    fun `NIST向量abc`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hex("abc".encodeToByteArray()),
        )
    }

    @Test
    fun `NIST向量长串跨块`() {
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()),
        )
    }
}

/**
 * 判据用例全部钉在**线上真实回包**上（2026-09-19 用站内标题 `OVA催眠性指導 ＃5 [中文字幕]`
 * 实测：`keyword=OVA催眠性指導` 返回 0 条，`keyword=催眠性指導` 返回唯一 1 条
 * `animeId 14432 / 催眠性指导`，其剧集为 `第1话…第6话`）。
 * 这不是编出来的形状 —— 繁简差异 + 类型前缀就是自动匹配的真实失效方式。
 */
class DandanplayMatcherTest {

    private val realEpisodes = listOf(
        DandanEpisode(144320001, "第1话 小幡優衣の場合"),
        DandanEpisode(144320002, "第2话 倉敷玲奈の場合"),
        DandanEpisode(144320003, "第3话 宮島桜の場合"),
        DandanEpisode(144320004, "第4话 宮島椿の場合"),
        DandanEpisode(144320005, "第5话 小幡夏美 / 倉敷麗華"),
        DandanEpisode(144320006, "第6话 Takamine Misao / Nozaki Yuu"),
    )

    @Test
    fun `清洗去社团前缀与尾部集号`() {
        assertEquals(
            "Columbina & Sandrone",
            DandanplayMatcher.cleanTitle("[Keke Animations] Columbina & Sandrone #48"),
        )
    }

    @Test
    fun `清洗去任意位置的括号段`() {
        // 站内标题把 `[中文字幕]` 放末尾，只削开头的话搜不出来
        assertEquals(
            "OVA催眠性指導",
            DandanplayMatcher.cleanTitle("[字幕组]OVA催眠性指導 ＃5 [中文字幕]"),
        )
    }

    @Test
    fun `清洗取第一段`() {
        assertEquals("命运石之门", DandanplayMatcher.cleanTitle("命运石之门 / Steins;Gate"))
    }

    @Test
    fun `集号解析覆盖常见标记`() {
        listOf(
            "OVA催眠性指導 ＃5" to 5,
            "Columbina & Sandrone #48" to 48,
            "催眠性指导 第5话" to 5,
            "某番 第12話" to 12,
            "Steins;Gate EP7" to 7,
            "某番剧 - 03" to 3,
            "Columbina & Sandrone 48" to 48,
        ).forEach { (title, expected) ->
            assertEquals(expected, DandanplayMatcher.parseEpisodeNumber(title), title)
        }
    }

    @Test
    fun `集号解析不把编号型标题当集数`() {
        // 这些是"标题本身就带数字"，当集号会关联到完全不相干的一集
        assertNull(DandanplayMatcher.parseEpisodeNumber("Re:0"))
        assertNull(DandanplayMatcher.parseEpisodeNumber("某动画 2019"))
        assertNull(DandanplayMatcher.parseEpisodeNumber("无数字标题"))
    }

    @Test
    fun `检索词备选去掉类型前缀`() {
        assertEquals(
            listOf("OVA催眠性指導", "催眠性指導"),
            DandanplayMatcher.searchQueries("OVA催眠性指導"),
        )
        // TVB 是真名开头，不能被啃掉前三个字母
        assertEquals(listOf("TVB剧本"), DandanplayMatcher.searchQueries("TVB剧本"))
        assertEquals(listOf("干净标题"), DandanplayMatcher.searchQueries("干净标题"))
    }

    @Test
    fun `归一化去标点全角与大小写`() {
        assertEquals("ova催眠性指導5", DandanplayMatcher.normalizeForCompare("OVA催眠性指導 ＃5"))
        // 全角字母折半角、全角数字照收、全角冒号当标点丢掉
        assertEquals("re0", DandanplayMatcher.normalizeForCompare("Ｒｅ：０"))
    }

    @Test
    fun `唯一候选且近乎同序才采纳繁简差异`() {
        val candidates = listOf(DandanAnime(animeId = 14432, animeTitle = "催眠性指导"))
        // `指導` vs `指导` 字面互不包含，靠"整条子序列同序"这一档得分
        assertEquals(
            14432,
            DandanplayMatcher.pickSubject(candidates, "OVA催眠性指導")?.animeId,
        )
    }

    @Test
    fun `唯一候选但明显不相干时不采纳`() {
        assertNull(
            DandanplayMatcher.pickSubject(
                listOf(DandanAnime(animeId = 1, animeTitle = "夏目友人帐")),
                "OVA催眠性指導",
            ),
        )
        // 只共享前缀的长短语：LCS 比例不到门槛，不能因为"库里就一条"将就
        assertNull(
            DandanplayMatcher.pickSubject(
                listOf(DandanAnime(animeId = 1, animeTitle = "foobarqux")),
                "foobarbaz",
            ),
        )
    }

    @Test
    fun `完全相等优先于相近候选`() {
        val candidates = listOf(
            DandanAnime(animeId = 1, animeTitle = "伪物语"),
            DandanAnime(animeId = 2, animeTitle = "化物语"),
        )
        assertEquals(2, DandanplayMatcher.pickSubject(candidates, "化物语")?.animeId)
    }

    @Test
    fun `同分多候选交人工选集`() {
        val candidates = listOf(
            DandanAnime(animeId = 1, animeTitle = "催眠性指导 OVA"),
            DandanAnime(animeId = 2, animeTitle = "催眠性指导 剧场版"),
        )
        assertNull(DandanplayMatcher.pickSubject(candidates, "催眠性指导"))
    }

    @Test
    fun `按集号对齐剧集`() {
        assertEquals(144320005L, DandanplayMatcher.pickEpisode(realEpisodes, 5)?.episodeId)
        assertEquals(144320001L, DandanplayMatcher.pickEpisode(realEpisodes, 1)?.episodeId)
        // 站内标了 #7 而库里只有 6 集 —— 说明对不上，不是让我们取最后一集凑数
        assertNull(DandanplayMatcher.pickEpisode(realEpisodes, 7))
    }

    @Test
    fun `解析不出集号时不猜多集番剧`() {
        assertNull(DandanplayMatcher.pickEpisode(realEpisodes, null))
        assertEquals(
            999L,
            DandanplayMatcher.pickEpisode(listOf(DandanEpisode(999, "第1话")), null)?.episodeId,
        )
        assertNull(DandanplayMatcher.pickEpisode(emptyList(), 1))
    }

    @Test
    fun `同号多条不猜`() {
        val sameNumberEpisodes = listOf(
            DandanEpisode(1, "第5话 前篇"),
            DandanEpisode(2, "第5话 后篇"),
        )
        assertNull(DandanplayMatcher.pickEpisode(sameNumberEpisodes, 5))
    }
}

/**
 * 召回放宽用例：此前"过于严苛"真实卡住的形状。
 *
 * 判据方向不变（错关联比不关联坏得多），放宽的是**漏斗口** ——
 * 多给官方检索几次不同问法的机会、多给集号对齐几次裁决的机会，
 * 而最终采纳仍要标题分过门槛 + 集号精确对上。
 */
class DandanplayMatcherRecallTest {

    @Test
    fun `尾部噪音词多给一次短查询`() {
        assertEquals(
            listOf("某番 特典", "某番"),
            DandanplayMatcher.searchQueries("某番 特典"),
        )
        assertEquals(
            listOf("某动画 2019", "某动画"),
            DandanplayMatcher.searchQueries("某动画 2019"),
        )
    }

    @Test
    fun `全标题实在太长退到最长分词`() {
        assertEquals(
            listOf("Columbina & Sandrone", "Columbina"),
            DandanplayMatcher.searchQueries("Columbina & Sandrone"),
        )
    }

    @Test
    fun `单字标题不发短查询`() {
        assertEquals(listOf("缘"), DandanplayMatcher.searchQueries("缘"))
    }

    @Test
    fun `中段集号只取作品名`() {
        val raw = "小女ラムネ 第7話 コマコとエッチなお約束 [中文字幕]"
        assertEquals("小女ラムネ", DandanplayMatcher.cleanTitle(raw))
        assertEquals(7, DandanplayMatcher.parseEpisodeNumber(raw))
        assertEquals(listOf("小女ラムネ"), DandanplayMatcher.searchQueries("小女ラムネ"))
    }

    @Test
    fun `开头集号不动`() {
        assertEquals("第5話 作品名", DandanplayMatcher.cleanTitle("第5話 作品名"))
        assertEquals("作品名", DandanplayMatcher.longestToken("第5話 作品名"))
    }

    @Test
    fun `季度后缀不当中段集号切掉`() {
        assertEquals("夏目友人帐第3期", DandanplayMatcher.cleanTitle("夏目友人帐第3期"))
        assertEquals("夏目友人帐 第3期", DandanplayMatcher.cleanTitle("夏目友人帐 第3期"))
    }

    @Test
    fun `纯数字分词不当查询`() {
        assertEquals(listOf("2019 某动画", "某动画"), DandanplayMatcher.searchQueries("2019 某动画"))
    }

    @Test
    fun `中段EP与短横集号`() {
        assertEquals("某番", DandanplayMatcher.cleanTitle("某番 EP7 特典版"))
        assertEquals("某番", DandanplayMatcher.cleanTitle("某番 - 03 特典"))
        // 破折号前没空白：`K-1` 是名字不是集号，不切
        assertEquals("K-1 World", DandanplayMatcher.cleanTitle("K-1 World"))
    }

    @Test
    fun `全角集号解析`() {
        assertEquals(5, DandanplayMatcher.parseEpisodeNumber("OVA催眠性指導 ＃５"))
        assertEquals("OVA催眠性指導", DandanplayMatcher.cleanTitle("OVA催眠性指導 ＃５ [中文字幕]"))
    }

    @Test
    fun `汉字数字集号解析`() {
        assertEquals(5, DandanplayMatcher.parseEpisodeNumber("某番 第五話"))
        assertEquals(12, DandanplayMatcher.parseEpisodeNumber("某番 第十二話"))
        assertEquals(25, DandanplayMatcher.parseEpisodeNumber("某番 第二十五回"))
        assertEquals(10, DandanplayMatcher.parseEpisodeNumber("某番 第十話"))
        assertEquals("某番", DandanplayMatcher.cleanTitle("某番 第五話"))
    }

    @Test
    fun `汉字数字拒绝歧义写法`() {
        assertNull(DandanplayMatcher.parseKanjiNumber("二五"))
        assertNull(DandanplayMatcher.parseKanjiNumber(""))
        assertNull(DandanplayMatcher.parseKanjiNumber("甲"))
        assertEquals(100, DandanplayMatcher.parseKanjiNumber("一百"))
    }

    @Test
    fun `Vol标记解析`() {
        assertEquals(2, DandanplayMatcher.parseEpisodeNumber("某番 Vol.2"))
        // 与既有"第X巻"一致：卷即集
        assertEquals(1, DandanplayMatcher.parseEpisodeNumber("某番 第1巻"))
    }

    @Test
    fun `排名按分排序并滤掉垃圾`() {
        val ranked = DandanplayMatcher.rankSubjects(
            listOf(
                DandanAnime(animeId = 1, animeTitle = "催眠性指导 OVA"),
                DandanAnime(animeId = 2, animeTitle = "夏目友人帐"),
                DandanAnime(animeId = 3, animeTitle = "催眠性指导"),
            ),
            "催眠性指导",
        )
        assertEquals(listOf(3, 1), ranked.map { it.subject.animeId })
    }

    private fun group(id: Int, title: String, vararg episodes: Pair<Long, String>) =
        DandanSubjectEpisodes(
            animeId = id,
            animeTitle = title,
            episodes = episodes.map { (episodeId, episodeTitle) -> DandanEpisode(episodeId, episodeTitle) },
        )

    @Test
    fun `并列番剧由集号裁决`() {
        // 旧逻辑：两个同分候选直接 null；新逻辑：只有 A 组含第 5 集，采纳 A
        val ref = DandanplayMatcher.pickAlignedEpisode(
            groups = listOf(
                group(1, "催眠性指导 OVA", 501L to "第5话 小幡夏美"),
                group(2, "催眠性指导 剧场版", 301L to "第3话 宫岛樱"),
            ),
            query = "催眠性指导",
            episodeNumber = 5,
        )
        assertNotNull(ref)
        assertEquals("501", ref.episodeId)
        assertEquals("催眠性指导 OVA", ref.subjectTitle)
    }

    @Test
    fun `并列且集号两边都有不猜`() {
        assertNull(
            DandanplayMatcher.pickAlignedEpisode(
                groups = listOf(
                    group(1, "催眠性指导 OVA", 501L to "第5话 A"),
                    group(2, "催眠性指导 剧场版", 502L to "第5话 B"),
                ),
                query = "催眠性指导",
                episodeNumber = 5,
            ),
        )
    }

    @Test
    fun `并列且集号两边都没有不猜`() {
        assertNull(
            DandanplayMatcher.pickAlignedEpisode(
                groups = listOf(
                    group(1, "催眠性指导 OVA", 501L to "第1话 A"),
                    group(2, "催眠性指导 剧场版", 502L to "第1话 B"),
                ),
                query = "催眠性指导",
                episodeNumber = 5,
            ),
        )
    }

    @Test
    fun `无集号时单组单集才敢拿`() {
        val hit = DandanplayMatcher.pickAlignedEpisode(
            groups = listOf(group(9, "某番", 901L to "第1话")),
            query = "某番",
            episodeNumber = null,
        )
        assertEquals("901", assertNotNull(hit).episodeId)
        // 多集、无集号：沿用"不猜"
        assertNull(
            DandanplayMatcher.pickAlignedEpisode(
                groups = listOf(
                    group(9, "某番", 901L to "第1话", 902L to "第2话"),
                ),
                query = "某番",
                episodeNumber = null,
            ),
        )
        // 多组、无集号：同样不猜
        assertNull(
            DandanplayMatcher.pickAlignedEpisode(
                groups = listOf(
                    group(9, "某番 A", 901L to "第1话"),
                    group(8, "某番 B", 801L to "第1话"),
                ),
                query = "某番",
                episodeNumber = null,
            ),
        )
    }

    @Test
    fun `垃圾分组直接过滤`() {
        assertNull(
            DandanplayMatcher.pickAlignedEpisode(
                groups = listOf(group(1, "夏目友人帐", 101L to "第5话")),
                query = "OVA催眠性指導",
                episodeNumber = 5,
            ),
        )
        assertNull(
            DandanplayMatcher.pickAlignedEpisode(
                groups = emptyList(),
                query = "OVA催眠性指導",
                episodeNumber = 5,
            ),
        )
    }
}

class DanmakuAutoMissCacheTest {
    @Test
    fun `落空记忆与过期`() {
        var now = 1_000L
        val cache = DanmakuAutoMissCache(now = { now })
        assertFalse(cache.isFresh("v1"))
        cache.mark("v1")
        assertTrue(cache.isFresh("v1"))
        now += 23 * 60 * 60 * 1000L
        assertTrue(cache.isFresh("v1"))
        now += 2 * 60 * 60 * 1000L
        assertFalse(cache.isFresh("v1"))
    }

    @Test
    fun `取消关联后记忆清掉`() {
        var now = 1_000L
        val cache = DanmakuAutoMissCache(now = { now })
        cache.mark("v1")
        cache.clear("v1")
        assertFalse(cache.isFresh("v1"))
    }
}

class DandanCommentParseTest {
    @Test
    fun `滚动弹幕解析`() {
        val item = DandanComment(cid = 1, p = "39.5,1,16777215,12345", m = "前方高能").toItem()
        assertNotNull(item)
        assertEquals(39500L, item.playTimeMillis)
        assertEquals(DanmakuLocation.SCROLL, item.location)
        assertEquals(0xFFFFFFFF.toInt(), item.color)
    }

    @Test
    fun `顶部底部模式映射`() {
        assertEquals(
            DanmakuLocation.BOTTOM,
            DandanComment(cid = 2, p = "10,4,0,1", m = "x").toItem()?.location,
        )
        assertEquals(
            DanmakuLocation.TOP,
            DandanComment(cid = 3, p = "10,5,0,1", m = "x").toItem()?.location,
        )
    }

    @Test
    fun `空文本与坏时间丢弃`() {
        assertNull(DandanComment(cid = 4, p = "10,1,0,1", m = "  ").toItem())
        assertNull(DandanComment(cid = 5, p = "oops", m = "x").toItem())
    }

    /**
     * 固定向量，不是"两次相等"就完事：确定性对两种错误编码都成立，
     * 而服务端只会回一句 `Invalid Signature`。期望值由官方算法
     * `base64(sha256(AppId + Timestamp + Path + AppSecret))` 独立算出。
     */
    @Test
    fun `签名匹配官方固定向量`() {
        assertEquals(
            "j5ZHx6+oS+DNht6giSVnpHr7jCccRMb218WKn5ykQBQ=",
            dandanplaySignature("appId", 1700000000L, "/api/v2/search/anime", "secret"),
        )
    }
}

/**
 * `/api/v2/search/episodes` 的真实回包形状。
 *
 * 这一条是**打真实接口才发现的**：剧集嵌在每个命中的 anime 下面，而模型按顶层
 * `episodes` 建模，配上 `coerceInputValues` 之后缺字段静默变空列表 —— 人工选集
 * 的第二步于是永远"查无此番"。所以拿原样回包钉住，别再用编造的 JSON。
 */
class DandanplayPayloadTest {
    @Test
    fun `剧集搜索回包按番剧分组`() {
        val response = Json { ignoreUnknownKeys = true }.decodeFromString<DandanEpisodeSearchResponse>(
            """
            {
              "hasMore": false,
              "animes": [
                {
                  "animeId": 17617,
                  "animeTitle": "葬送的芙莉莲",
                  "episodes": [
                    {"episodeId": 176170001, "episodeTitle": "第1话 冒险结束"},
                    {"episodeId": 176170002, "episodeTitle": "第2话 不见得一定是靠魔法…"}
                  ]
                }
              ],
              "errorCode": 0,
              "success": true,
              "errorMessage": ""
            }
            """.trimIndent(),
        )
        assertEquals(1, response.animes.size)
        val subject = response.animes.first()
        assertEquals(17617, subject.animeId)
        assertEquals("葬送的芙莉莲", subject.animeTitle)
        assertEquals(listOf(176170001L, 176170002L), subject.episodes.map { it.episodeId })
        assertEquals("第1话 冒险结束", subject.episodes.first().episodeTitle)
    }
}

class DanmakuProviderAssemblyTest {
    @Test
    fun `代理地址只接受http与https`() {
        assertEquals("https://danmaku.example.com", "https://danmaku.example.com///".asValidBaseUrl())
        assertEquals("http://127.0.0.1:8080", " http://127.0.0.1:8080 ".asValidBaseUrl())
        assertNull("danmaku.example.com".asValidBaseUrl())
        assertNull("file:///sdcard/x".asValidBaseUrl())
        assertNull("javascript:alert(1)".asValidBaseUrl())
        assertNull("".asValidBaseUrl())
    }

    /**
     * "未配置"必须**显式构造**：凭据默认值现在来自构建期注入，
     * 写 `AppSettings()` 会让这个用例在内置了凭据的构建里正好反过来。
     */
    private fun unconfigured() = AppSettings(danmakuAppId = "", danmakuAppSecret = "")

    @Test
    fun `未配置时功能休眠`() {
        assertNull(DanmakuProvider.from(unconfigured()))
        // 半填的凭据不成立，也没有代理 → 依然休眠
        assertNull(DanmakuProvider.from(unconfigured().copy(danmakuAppId = "only-id")))
        // 非法代理也不能让它带着坏 URL 上线
        assertNull(DanmakuProvider.from(unconfigured().copy(danmakuProxyBase = "ftp://x")))
    }
}

/**
 * 凭据的**读侧**规则，钉的是升级路径：改为构建期注入之前跑过一次的那份存档里
 * 躺着 `danmaku_app_id=""`（首启会把全部默认值落盘），它一度把内置凭据整个顶掉，
 * 于是 `DanmakuProvider.from` 判为未配置 —— 播放器上的表现就是"根本没有弹幕"。
 */
class DanmakuCredentialReadTest {

    @Test
    fun `空串不顶掉内置凭据`() {
        // 全新安装：键根本不存在
        assertEquals(
            "built-id" to "built-secret",
            resolveDanmakuCredentials(null, null, "built-id", "built-secret"),
        )
        // 旧版本落盘的空串：曾经的 bug 现场
        assertEquals(
            "built-id" to "built-secret",
            resolveDanmakuCredentials("", "", "built-id", "built-secret"),
        )
        // 用户手动清空 = 回到内置
        assertEquals(
            "built-id" to "built-secret",
            resolveDanmakuCredentials("   ", "", "built-id", "built-secret"),
        )
    }

    @Test
    fun `用户自己的成对凭据优先`() {
        assertEquals(
            "my-id" to "my-secret",
            resolveDanmakuCredentials("my-id", "my-secret", "built-id", "built-secret"),
        )
    }

    @Test
    fun `半填不跟内置拼成一对`() {
        // 只填 ID 却配上内置 secret，换来的只会是一次更难查的 403
        val stored = resolveDanmakuCredentials("my-id", "", "built-id", "built-secret")
        assertEquals("my-id" to "", stored)
        assertNull(
            DanmakuProvider.from(
                AppSettings(danmakuAppId = stored.first, danmakuAppSecret = stored.second),
            ),
        )
    }

    @Test
    fun `内置凭据不回写磁盘`() {
        val builtIn = resolveDanmakuCredentials(null, null, "built-id", "built-secret")
        // 上一版把解析后的内置凭据原样落了盘 ⇒ 本次必须洗回空串，
        // 否则以后换构建期注入的那对，会被这份"看着像用户手填"的旧值顶掉
        assertEquals(
            "" to "",
            danmakuCredentialsToPersist(builtIn.first, builtIn.second, "built-id", "built-secret"),
        )
        assertEquals(
            "my-id" to "my-secret",
            danmakuCredentialsToPersist("my-id", "my-secret", "built-id", "built-secret"),
        )
    }

    @Test
    fun `读写往返不改变生效凭据`() {
        listOf(
            null to null,
            "my-id" to "my-secret",
        ).forEach { (storedId, storedSecret) ->
            val read = resolveDanmakuCredentials(storedId, storedSecret, "built-id", "built-secret")
            val persisted = danmakuCredentialsToPersist(read.first, read.second, "built-id", "built-secret")
            assertEquals(
                read,
                resolveDanmakuCredentials(persisted.first, persisted.second, "built-id", "built-secret"),
            )
        }
    }
}

/**
 * 评论投影映射：时间是排出来的，测的是"排得稳不稳" ——
 * 确定性（同输入同输出）、有序（引擎前置条件）、截断与上下限。
 */
class CommentDanmakuMapperTest {

    private fun comment(
        user: String,
        text: String,
        likes: Int? = null,
        child: Boolean = false,
    ) = lovehan1me.core.domain.model.VideoComments.VideoComment(
        avatar = "",
        username = user,
        date = "2026-01-01",
        content = text,
        thumbUp = likes,
        isChildComment = child,
        post = lovehan1me.core.domain.model.VideoComments.VideoComment.POST(),
    )

    @Test
    fun `空评论与过短片长直接返回空`() {
        assertEquals(emptyList(), CommentDanmakuMapper.map(emptyList(), 600_000L))
        assertEquals(
            emptyList(),
            CommentDanmakuMapper.map(listOf(comment("a", "好评")), 20_000L),
        )
    }

    @Test
    fun `除子评论外全量显示按赞排序`() {
        val comments = (1..65).map { comment("u$it", "评论$it", likes = it) }
        val items = CommentDanmakuMapper.map(comments, 600_000L)
        // 不设上限：65 条全投，赞最高的排最前
        assertEquals(65, items.size)
        assertEquals("评论65", items.first().text)
        assertTrue(items.all { it.source == DanmakuSource.COMMENT })
        assertTrue(items.all { it.id < 0L })
    }

    @Test
    fun `散布间隔均匀`() {
        val items = CommentDanmakuMapper.map(
            (1..10).map { comment("u$it", "评论$it", likes = it) },
            600_000L,
        )
        // 可用区间 [5000, 590000]，10 条 → 步长 58500，抖动 ±10% ⇒ 间隔恒在 [0.8, 1.2] 步长内
        val step = 58500.0
        val times = items.map { it.playTimeMillis }
        times.zipWithNext { a, b ->
            val gap = (b - a).toDouble()
            assertTrue(gap in step * 0.7..step * 1.3, "间隔 $gap 超出均匀带")
        }
    }

    @Test
    fun `输出有序且落在留白区间内`() {
        val items = CommentDanmakuMapper.map(
            (1..10).map { comment("u$it", "评论$it", likes = it) },
            600_000L,
        )
        assertEquals(10, items.size)
        val times = items.map { it.playTimeMillis }
        assertEquals(times.sorted(), times)
        assertTrue(times.all { it in 5_000L..590_000L })
    }

    @Test
    fun `同输入同输出`() {
        val comments = (1..20).map { comment("u$it", "评论内容 $it", likes = 20 - it) }
        assertEquals(
            CommentDanmakuMapper.map(comments, 600_000L),
            CommentDanmakuMapper.map(comments, 600_000L),
        )
    }

    @Test
    fun `子评论空白与超长被处理`() {
        val items = CommentDanmakuMapper.map(
            listOf(
                comment("child", "子评论不投影", child = true),
                comment("blank", "   "),
                comment("long", "超".repeat(50), likes = 99),
            ),
            600_000L,
        )
        assertEquals(1, items.size)
        assertEquals(CommentDanmakuMapper.MAX_TEXT_CHARS + 1, items.single().text.length)
        assertTrue(items.single().text.endsWith("…"))
    }
}
