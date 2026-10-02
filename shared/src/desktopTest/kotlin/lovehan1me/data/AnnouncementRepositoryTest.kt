package lovehan1me.data

import lovehan1me.core.domain.model.Announcement
import lovehan1me.core.domain.model.AnnouncementSeverity
import lovehan1me.ui.component.findLinkRanges
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [AnnouncementRepository.resolveVisible] 的规则级回归。
 *
 * 每条用例对应仓储里的一条规则，且都做过反向验证：把对应规则注释掉后，
 * 该用例必须**失败**。否则它就是个空用例 —— 本仓已经栽过一次
 * （下拉刷新的门控测试在去掉门控后依然全绿）。
 */
class AnnouncementRepositoryTest {

    /** 固定"现在"，让时效判定可重复。 */
    private val now = 1_800_000_000L

    private fun remote(vararg entries: String): String =
        "{\"announcements\":[" + entries.joinToString(",") + "]}"

    private fun entry(
        id: String,
        content: String = "c-$id",
        severity: String? = null,
        priority: Int = 1,
        timestamp: Long = 0,
        expiresAt: Long = 0,
    ): String =
        "{\"id\":\"$id\",\"title\":\"t-$id\",\"content\":\"$content\"," +
            "\"priority\":$priority,\"timestamp\":$timestamp,\"expiresAt\":$expiresAt" +
            (if (severity != null) ",\"severity\":\"$severity\"" else "") + "}"

    private fun visible(
        json: String?,
        legacy: Announcement? = null,
        readKeys: Collection<String> = emptyList(),
    ) = AnnouncementRepository.resolveVisible(json, legacy, readKeys, now)

    @Test
    fun `过期的公告不展示`() {
        val json = remote(
            entry(id = "alive", expiresAt = now + 60),
            entry(id = "dead", expiresAt = now - 1),
        )
        assertEquals(listOf("alive"), visible(json).map { it.id })
    }

    @Test
    fun `expiresAt 为 0 表示永不过期`() {
        val json = remote(entry(id = "forever", expiresAt = 0))
        assertEquals(listOf("forever"), visible(json).map { it.id })
    }

    @Test
    fun `远端与 legacy 的同一条公告只留一份_且远端那份胜出`() {
        val legacy = Announcement(id = "shared", title = "t-legacy", content = "c", isActive = true)
        val json = remote(entry(id = "shared", content = "c"))
        val result = visible(json, legacy)
        assertEquals(1, result.size)
        assertEquals("t-shared", result.single().title)
    }

    @Test
    fun `已读的公告不展示`() {
        val json = remote(entry(id = "a"), entry(id = "b"))
        assertEquals(listOf("b"), visible(json, readKeys = listOf("a")).map { it.id })
    }

    @Test
    fun `排序为 阻断级在前_再按 priority 升序_再按时间降序`() {
        val json = remote(
            entry(id = "info", severity = "info", priority = 0),
            entry(id = "normal-late", severity = "normal", priority = 5, timestamp = 200),
            entry(id = "normal-early", severity = "normal", priority = 5, timestamp = 100),
            entry(id = "normal-first", severity = "normal", priority = 1, timestamp = 1),
            entry(id = "blocking", severity = "blocking", priority = 9),
        )
        assertEquals(
            listOf("blocking", "normal-first", "normal-late", "normal-early", "info"),
            visible(json).map { it.id },
        )
    }

    @Test
    fun `未知 severity 降级为 Normal 而不是阻断`() {
        val json = remote(entry(id = "weird", severity = "catastrophic"))
        assertEquals(AnnouncementSeverity.Normal, visible(json).single().severity)
    }

    @Test
    fun `坏 JSON 视为没有远端公告而不是抛异常`() {
        assertTrue(visible("{not json at all").isEmpty())
    }

    @Test
    fun `裸数组形状也能解析`() {
        val json = "[" + entry(id = "bare") + "]"
        assertEquals(listOf("bare"), visible(json).map { it.id })
    }

    @Test
    fun `没有正文的公告被丢弃`() {
        val json = remote(entry(id = "empty", content = ""))
        assertTrue(visible(json).isEmpty())
    }

    @Test
    fun `isActive 为 false 的 legacy 公告被丢弃`() {
        val legacy = Announcement(title = "t", content = "c", isActive = false)
        assertTrue(visible(null, legacy).isEmpty())
    }

    @Test
    fun `legacy 与远端都为空时结果为空`() {
        assertTrue(visible(null).isEmpty())
        assertTrue(visible("{\"announcements\":[]}").isEmpty())
    }

    /**
     * stableKey 会经 DataStore 用逗号拼接落盘（`announcement_read_keys`），
     * 所以它自己不能含逗号或换行，否则存下去 1 条、读回来变好几条。
     */
    @Test
    fun `无 id 时 stableKey 不含逗号与换行`() {
        val announcement = Announcement(
            title = "标题, 带逗号",
            content = "第一行\n第二行",
            isActive = true,
        )
        val key = announcement.stableKey
        assertTrue(',' !in key, "key 含逗号会被 DataStore 切开：$key")
        assertTrue('\n' !in key, "key 含换行会破坏单行存储：$key")
    }

    @Test
    fun `无 id 公告的 stableKey 跨实例稳定`() {
        val a = Announcement(title = "t", content = "c", isActive = true)
        val b = Announcement(title = "t", content = "c", isActive = true)
        assertEquals(a.stableKey, b.stableKey)
    }

    private fun linkTexts(text: String) =
        findLinkRanges(text).map { text.substring(it.first, it.last + 1) }

    @Test
    fun `链接区间覆盖完整路径而不是只到域名`() {
        val text = "见 https://example.com/some/path?q=1 谢谢"
        assertEquals(listOf("https://example.com/some/path?q=1"), linkTexts(text))
    }

    @Test
    fun `句末标点不被算进链接`() {
        assertEquals(listOf("https://example.com/a"), linkTexts("详见 https://example.com/a。"))
        assertEquals(listOf("https://example.com/a"), linkTexts("详见 https://example.com/a, 然后"))
    }

    @Test
    fun `没有链接时返回空区间`() {
        assertTrue(findLinkRanges("纯文字，没有链接").isEmpty())
    }
}
