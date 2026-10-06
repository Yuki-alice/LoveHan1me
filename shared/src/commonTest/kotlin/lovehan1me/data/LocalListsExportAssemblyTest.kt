package lovehan1me.data

import lovehan1me.data.database.dao.LocalPlaylistRow
import lovehan1me.data.database.entity.LocalListItemEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// B7-N1 守卫：导出组装必须与"逐表 getItems"同语义 —— 每表内 addedAt DESC、
// 空表给空列表、未知桶丢弃。输入是 DAO 一次拿到的全局 DESC 全表。
class LocalListsExportAssemblyTest {

    private fun item(listCode: String, videoCode: String, addedAt: Long) =
        LocalListItemEntity(
            listCode = listCode,
            videoCode = videoCode,
            title = "t-$videoCode",
            coverUrl = "https://img/$videoCode.jpg",
            addedAt = addedAt,
        )

    private fun playlistRow(listCode: String) =
        LocalPlaylistRow(
            listCode = listCode,
            title = "p-$listCode",
            desc = "d-$listCode",
            createdAt = 0L,
            updatedAt = 0L,
            total = 0,
            coverUrl = null,
        )

    @Test
    fun `交错的全表按桶分组且每桶保持DESC`() {
        // 全局 DESC 序列里三桶交错 —— 分组后每桶仍是 DESC（groupBy 保遭遇顺序）。
        val all = listOf(
            item("save", "v1", 300L),
            item("local_a", "v2", 290L),
            item("likes", "v3", 280L),
            item("save", "v4", 270L),
            item("local_a", "v5", 260L),
            item("likes", "v6", 250L),
        )
        val export = assembleListsExport(all, listOf(playlistRow("local_a")))

        assertEquals(listOf("v1", "v4"), export.watchLater.map { it.videoCode })
        assertEquals(listOf("v3", "v6"), export.favorites.map { it.videoCode })
        assertEquals(1, export.playlists.size)
        assertEquals("p-local_a", export.playlists[0].title)
        assertEquals("d-local_a", export.playlists[0].desc)
        assertEquals(listOf("v2", "v5"), export.playlists[0].items.map { it.videoCode })
        // 字段透传不断（抽查一条）。
        assertEquals("t-v1", export.watchLater[0].title)
        assertEquals(300L, export.watchLater[0].addedAt)
    }

    @Test
    fun `空表给空列表_未知桶丢弃`() {
        val all = listOf(
            item("save", "v1", 100L),
            // 不在任何已知桶里：旧实现根本不会去查它，新实现必须同样丢弃。
            item("ghost", "vx", 90L),
        )
        val export = assembleListsExport(
            all,
            listOf(playlistRow("local_empty"), playlistRow("local_a")),
        )

        assertEquals(listOf("v1"), export.watchLater.map { it.videoCode })
        assertTrue(export.favorites.isEmpty())
        assertEquals(2, export.playlists.size)
        assertTrue(export.playlists.all { it.items.isEmpty() })
        val allCodes = export.watchLater + export.favorites + export.playlists.flatMap { it.items }
        assertTrue(allCodes.none { it.videoCode == "vx" })
    }

    @Test
    fun `与逐表查询逐表拼装完全等价`() {
        // 正向对照：同一批数据按旧实现（逐表 ORDER BY addedAt DESC 再拼）算一遍，
        // 必须与新实现（一次全表 + 分组）逐字段一致。改了排序或分组即转红。
        val codes = listOf("save", "likes", "local_a", "local_b")
        val all = (0 until 200).map { i ->
            item(codes[i % codes.size], "v$i", 10_000L - i * 37L)
        }.sortedByDescending { it.addedAt }
        val rows = listOf(playlistRow("local_a"), playlistRow("local_b"))

        val actual = assembleListsExport(all, rows)

        fun oldWay(listCode: String) =
            all.filter { it.listCode == listCode }
                .sortedByDescending { it.addedAt }
                .map { it.toExport() }
        assertEquals(oldWay("save"), actual.watchLater)
        assertEquals(oldWay("likes"), actual.favorites)
        assertEquals(rows.map { it.title }, actual.playlists.map { it.title })
        assertEquals(rows.map { it.desc }, actual.playlists.map { it.desc })
        assertEquals(
            listOf(oldWay("local_a"), oldWay("local_b")),
            actual.playlists.map { it.items },
        )
    }

    @Test
    fun `空输入导出空壳`() {
        val export = assembleListsExport(emptyList(), emptyList())
        assertTrue(export.watchLater.isEmpty())
        assertTrue(export.favorites.isEmpty())
        assertTrue(export.playlists.isEmpty())
    }
}
