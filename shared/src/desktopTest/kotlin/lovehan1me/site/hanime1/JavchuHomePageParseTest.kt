package lovehan1me.site.hanime1

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import lovehan1me.core.constant.HanimeConstants
import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.HomePage
import lovehan1me.core.domain.model.SettingsStore
import lovehan1me.core.domain.state.WebsiteState
import lovehan1me.data.SettingsRepository
import lovehan1me.site.SiteIdentity
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 数据源切换回归：`Parser.homePageVer2` 的 **AV 分支**与 **14 行固定下标映射**。
 *
 * ---
 *
 * ### 为什么值得一条测试
 *
 * 这套解析逻辑有两处**静默失效**的历史：
 *
 * 1. `isAVSite` 原来写成 `SettingsRepository.baseUrl == HANIME_URL[3]`。开了自定义镜像后
 *    `baseUrl` 返回镜像地址，判定变 `false` —— javchu 首页会被按番剧站的**行下标**去取，
 *    不抛异常，只是各段内容错位 / 为空（见 [SiteIdentity] 的类 KDoc）；
 * 2. `homePageParse` 是**固定下标**映射（`getOrNull(0/1/2/…/13)`），而两站的行数不同：
 *    javchu 14 行、行 4 与行 9 是广告位。任何人动了 `Parser` 的取值下标、或上游改版
 *    增删一行，都只会表现为"某个板块空了"，不会报错。
 *
 * 本测试用真实抓取的 javchu 首页（`.workbuddy/_javchu_home.html`，见 [findJavchuHomeFixture]）
 * 把上面两件事都钉住：**每一段的非空**证明下标映射没错位，**AV / 番剧两条分支的
 * `newAnimeTrailer` 取行不同**证明 `isAVSite` 真的生效了。
 *
 * ### 跑法
 *
 * ```bash
 * ./gradlew :shared:desktopTest --tests '*JavchuHomePage*' --tests '*SiteIdentity*'
 * ```
 *
 * 夹具缺失时**跳过而不是失败**：`.workbuddy/` 在 `.gitignore` 里（本地抓包、313KB、
 * 不适合入库），换台机器 clone 下来本来就该没有 —— 与 `FormLoginLiveTest` /
 * `DesktopMpvPlaybackLiveTest` 的"环境不满足就打印跳过"同一约定。
 */
private class InMemorySettingsStore : SettingsStore {
    private val state = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = state
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

/** 同 JVM 跑多个测试类时只允许 install 一次（`SettingsRepository.install` 本身有 check）。 */
private fun installTestStore() {
    runCatching { SettingsRepository.install(InMemorySettingsStore()) }
}

/**
 * 自 `user.dir` 起向上逐级查找夹具 —— Gradle 的 test 工作目录默认是模块目录
 * （`shared/`），但 IDE 里可能被设成仓库根，写死相对层级会在其中一种下失效。
 */
private fun findJavchuHomeFixture(): File? {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
        val candidate = File(dir, ".workbuddy/_javchu_home.html")
        if (candidate.isFile) return candidate
        dir = dir.parentFile
    }
    return null
}

private fun readFixtureOrNull(): String? {
    val fixture = findJavchuHomeFixture()
    if (fixture == null) {
        println(
            "[skip] 未找到 .workbuddy/_javchu_home.html（本地抓包，gitignore 不入库）—— " +
                "跳过 javchu 首页解析回归。需要时按 docs/javchu数据源热切换实施规划.md 重新抓一份。"
        )
        return null
    }
    return fixture.readText()
}

/** 把当前域名切到 [domain]（内存 store，不碰 `~/.lovehan1me` 真实文件）。 */
private fun useDomain(domain: String) {
    installTestStore()
    runBlocking { SettingsRepository.update { it.copy(domainName = domain) } }
}

private fun parseHome(html: String): HomePage {
    val state = runBlocking { Parser.homePageVer2(html) }
    val success = state as? WebsiteState.Success<*> ?: error("首页解析失败：$state")
    return success.info as HomePage
}

/**
 * [SiteIdentity] 的纯字符串判定（不依赖夹具，也不依赖 [SettingsRepository] 是否装好）。
 */
class SiteIdentityTest {

    @Test
    fun `AV 站判定对尾斜杠_大小写_首尾空白都不敏感`() {
        // 这四种写法在历史上都会让 `baseUrl == HANIME_URL[3]` 判成 false。
        listOf(
            "https://javchu.com/",
            "https://javchu.com",
            "HTTPS://JAVCHU.COM/",
            "  https://javchu.com/  ",
        ).forEach { raw ->
            assertTrue(SiteIdentity.isAvSite(raw), "「$raw」应判为 AV 站")
        }

        listOf(
            "https://hanime1.me/",
            "https://hanime1.com/",
            "https://hanimeone.me/",
            "",
            // 自定义镜像地址：镜像不改变站点身份，但**不是** AV 站本身。
            "https://my-mirror.example.com/",
        ).forEach { raw ->
            assertFalse(SiteIdentity.isAvSite(raw), "「$raw」不应判为 AV 站")
        }
    }

    @Test
    fun `番剧站判定覆盖全部备选网域且不含 AV 站`() {
        HanimeConstants.ANIME_URL.forEach { url ->
            assertTrue(SiteIdentity.isAnimeSite(url), "「$url」应判为番剧站")
        }
        assertFalse(SiteIdentity.isAnimeSite(HanimeConstants.AV_URL), "AV 站不应判为番剧站")
    }
}

/**
 * 真站点 HTML 上的解析回归（夹具缺失则整体跳过）。
 */
class JavchuHomePageParseTest {

    /** 两站共有的 13 个影片板块（夹具里的 14 行去掉 2 个广告位）。 */
    private fun sectionsOf(page: HomePage): Map<String, List<HanimeInfo>> =
        mapOf(
            "latestRelease[0]" to page.latestRelease,
            "latestHanime[1]" to page.latestHanime,
            "ecchiAnime[2]" to page.ecchiAnime,
            "shortEpisodeAnime[3]" to page.shortEpisodeAnime,
            "motionAnime[5]" to page.motionAnime,
            "threeDCG[6]" to page.threeDCG,
            "twoPointFiveDAnime[7]" to page.twoPointFiveDAnime,
            "twoDAnime[8]" to page.twoDAnime,
            "aiGenerated[10]" to page.aiGenerated,
            "mmd[11]" to page.mmd,
            "cosplay[12]" to page.cosplay,
            "watchingNow[13]" to page.watchingNow,
        )

    @Test
    fun `javchu 首页按 AV 分支解析_十四个下标全部落到非空板块`() {
        val html = readFixtureOrNull() ?: return
        useDomain(HanimeConstants.AV_URL)
        assertTrue(
            SiteIdentity.isAvSite,
            "domainName 设为 AV_URL 后 SiteIdentity 应判为 AV 站 —— 这条红了说明身份判定又被镜像/baseUrl 干扰了",
        )

        val page = parseHome(html)
        val sections = sectionsOf(page)

        sections.forEach { (name, list) ->
            assertTrue(
                list.isNotEmpty(),
                "$name 为空 —— 固定下标映射错位（多半是落到了广告行 4/9 或越界）",
            )
            list.forEach { info ->
                assertTrue(
                    info.videoCode.isNotBlank() && info.videoCode.all(Char::isDigit),
                    "$name 里出现非法 videoCode=「${info.videoCode}」（$name 的 href 形态变了？）",
                )
                assertTrue(info.title.isNotBlank(), "$name 里出现空 title（videoCode=${info.videoCode}）")
                assertTrue(info.coverUrl.isNotBlank(), "$name 里出现空 coverUrl（videoCode=${info.videoCode}）")
                assertEquals(
                    HanimeInfo.NORMAL,
                    info.itemType,
                    "$name 走的是 extractHanimeInfo()，itemType 应为普通卡片",
                )
            }
        }
        println("[javchu] 各段数量=" + sections.entries.joinToString { "${it.key}=${it.value.size}" })

        // 刻意**不**断言 videoCode 互不重复：站点自己就会把同一条片子在多个板块里重复推
        // （本夹具的第 1/5/10 行都是 MIDA-474 的 404523/404522），第 6 行还留着
        // `watch?v=6` 这类未水合的占位 href。这些是**页面内容**的特征，不是解析缺陷 ——
        // 拿"去重"当断言只会把站点行为当成自己的 bug 来修。
    }

    @Test
    fun `newAnimeTrailer 的取行随 isAVSite 切换`() {
        val html = readFixtureOrNull() ?: return

        // AV 站：`getOrNull(if (isAVSite) 13 else 12)` 走 13，且走 `extractHanimeInfo()`，
        // 于是与同样取第 13 行的「他们在看」逐条相同。
        useDomain(HanimeConstants.AV_URL)
        val avPage = parseHome(html)
        assertTrue(avPage.watchingNow.isNotEmpty(), "AV 站的「他们在看」（第 13 行）不应为空")
        assertEquals(
            avPage.watchingNow.map { it.videoCode },
            avPage.newAnimeTrailer.map { it.videoCode },
            "AV 站应取第 13 行 —— 与「他们在看」同源",
        )

        // 番剧站：同一份 HTML、同一个 domainName 之外的一切都不变，取行应改为 12。
        useDomain(HanimeConstants.HANIME_URL[0])
        assertFalse(SiteIdentity.isAvSite, "domainName 设为番剧站后不应再判为 AV 站")
        val animePage = parseHome(html)
        assertEquals(
            avPage.watchingNow.map { it.videoCode },
            animePage.watchingNow.map { it.videoCode },
            "「他们在看」是第 13 行，与站点无关 —— 它变了说明夹具或下标逻辑被改动了",
        )
        assertNotEquals(
            animePage.watchingNow.map { it.videoCode },
            animePage.newAnimeTrailer.map { it.videoCode },
            "番剧站应取第 12 行，不该与「他们在看」同源 —— 这条红了说明 isAVSite 静默退化成 false",
        )
        println("[javchu] AV/番剧两分支取行确实不同（13 vs 12）")
    }
}
