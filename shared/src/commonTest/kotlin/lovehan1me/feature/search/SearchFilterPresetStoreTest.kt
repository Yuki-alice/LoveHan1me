package lovehan1me.feature.search

import lovehan1me.core.domain.model.SearchFilterPreset
import lovehan1me.core.domain.model.SearchFilterSnapshot
import lovehan1me.data.database.entity.HanimeAdvancedSearchHistoryEntity
import lovehan1me.data.database.entity.toSnapshot
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 命名筛选预设的纯逻辑测试。
 *
 * 只覆盖**不依赖** `SettingsRepository.install()` 的部分：[SearchFilterPresetStore] 的纯函数、
 * 预设落盘用的 JSON 往返形状、以及历史实体 → 快照的归约。
 * 「真的写进 DataStore 再读回来」那条路径要真 DataStore，不在这里（也不该在这里）覆盖。
 */
class SearchFilterPresetStoreTest {

    private fun preset(id: String, name: String, createdAt: Long = 0L) =
        SearchFilterPreset(id = id, name = name, createdAt = createdAt)

    // ------------------------------------------------------------ normalizeName

    @Test
    fun `normalizeName 去两端空白并折叠内部连续空白`() {
        assertEquals("夏日 合集", SearchFilterPresetStore.normalizeName("  夏日   合集 "))
    }

    @Test
    fun `normalizeName 遇全空白返回 null`() {
        assertNull(SearchFilterPresetStore.normalizeName("   "))
        assertNull(SearchFilterPresetStore.normalizeName(""))
        assertNull(SearchFilterPresetStore.normalizeName("\t\n "))
    }

    // ------------------------------------------------------------ upsert

    @Test
    fun `upsert 新名字追加到末尾`() {
        val result = SearchFilterPresetStore.upsert(
            existing = listOf(preset("a", "A")),
            incoming = preset("b", "B"),
        )
        assertEquals(listOf("A", "B"), result.map { it.name })
    }

    @Test
    fun `upsert 同名覆盖时沿用原 id 与原位置`() {
        val existing = listOf(preset("a", "A"), preset("b", "B"), preset("c", "C"))
        val result = SearchFilterPresetStore.upsert(existing, preset("brand-new", "B", createdAt = 99L))

        assertEquals(listOf("A", "B", "C"), result.map { it.name }, "位置不该变")
        assertEquals(
            "b",
            result[1].id,
            "id 要沿用旧的：UI 以 id 为 key，换 id 会让芯片被当成'删了又加'而重新动画",
        )
        assertEquals(0L, result[1].createdAt, "createdAt 也该沿用旧的")
    }

    @Test
    fun `upsert 同名覆盖会换掉内容`() {
        val existing = listOf(
            SearchFilterPreset(
                id = "a", name = "A", createdAt = 1L,
                snapshot = SearchFilterSnapshot(genre = "裏番"),
            ),
        )
        val result = SearchFilterPresetStore.upsert(
            existing,
            SearchFilterPreset(
                id = "b", name = "A", createdAt = 2L,
                snapshot = SearchFilterSnapshot(genre = "泡麵番"),
            ),
        )
        assertEquals(1, result.size, "同名是覆盖而不是新增")
        assertEquals("泡麵番", result.single().snapshot.genre)
    }

    @Test
    fun `upsert 超出上限时丢掉最早的一条`() {
        val existing = (1..SearchFilterPresetStore.MAX_PRESETS).map {
            preset("id$it", "P$it", createdAt = it.toLong())
        }
        val result = SearchFilterPresetStore.upsert(existing, preset("new", "NEW", createdAt = 999L))

        assertEquals(SearchFilterPresetStore.MAX_PRESETS, result.size)
        assertFalse(result.any { it.name == "P1" }, "最早插入的那条应被挤掉")
        assertEquals("NEW", result.last().name, "新写入的必须还在")
    }

    // ------------------------------------------------------------ removeById

    @Test
    fun `removeById 只删指定 id`() {
        val existing = listOf(preset("a", "A"), preset("b", "B"))
        assertEquals(
            listOf("B"),
            SearchFilterPresetStore.removeById(existing, "a").map { it.name },
        )
    }

    @Test
    fun `removeById 遇不存在的 id 原样返回`() {
        val existing = listOf(preset("a", "A"))
        assertEquals(existing, SearchFilterPresetStore.removeById(existing, "nope"))
    }

    // ------------------------------------------------------------ 持久化形状

    @Test
    fun `预设列表能原样 JSON 往返`() {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val source = listOf(
            SearchFilterPreset(
                id = "1234567-42",
                name = "深夜向",
                createdAt = 123L,
                snapshot = SearchFilterSnapshot(
                    query = "abc",
                    genre = "裏番",
                    sort = "最受歡迎",
                    broad = true,
                    date = "2026 年 3 月",
                    duration = "5 分鐘 +",
                    tags = "巨乳,中出",
                    brands = "ZIZ",
                ),
            ),
        )
        assertEquals(
            source,
            json.decodeFromString<List<SearchFilterPreset>>(json.encodeToString(source)),
        )
    }

    @Test
    fun `空预设列表往返后仍是空列表`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        assertEquals(
            emptyList(),
            json.decodeFromString<List<SearchFilterPreset>>(json.encodeToString(emptyList<SearchFilterPreset>())),
        )
    }

    // ------------------------------------------------------------ 快照语义

    @Test
    fun `快照 isEmpty 把空串与 null 都算作无筛选`() {
        assertTrue(SearchFilterSnapshot().isEmpty)
        assertTrue(SearchFilterSnapshot(genre = "", query = "   ").isEmpty, "空串/纯空白不算筛选")
        assertFalse(SearchFilterSnapshot(genre = "裏番").isEmpty)
        assertFalse(SearchFilterSnapshot(broad = true).isEmpty)
        assertFalse(SearchFilterSnapshot(tags = "巨乳").isEmpty)
        assertFalse(SearchFilterSnapshot(brands = "ZIZ").isEmpty)
    }

    @Test
    fun `历史实体归约成快照时把可空 broad 归一`() {
        val snapshot = HanimeAdvancedSearchHistoryEntity(
            query = "q",
            broad = null,
            tags = "巨乳,中出",
        ).toSnapshot()

        assertFalse(snapshot.broad, "表里 broad 可空（历史遗留），快照里必须非空")
        assertEquals("q", snapshot.query)
        assertEquals("巨乳,中出", snapshot.tags, "wire 串原样带过来，解析留给恢复处")
    }
}
