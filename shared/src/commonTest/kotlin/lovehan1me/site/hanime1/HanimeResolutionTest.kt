package lovehan1me.site.hanime1

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * P0-2：清晰度归一化 + 未知档不丢 + HLS 感知的语料门禁。
 *
 * 钉住的三条不变式：
 * ① 已知档大小写/空格/缺 P 全收敛（站点改个大小写不能丢画质）；
 * ② 未知数字档（2K/4K）进 extras，不占固定槽、不丢；
 * ③ HLS（m3u8/mpegurl）subtype 保留，供播放器判 HLS，分片 ts 不误判。
 */
class HanimeResolutionTest {

    @Test
    fun `已知档归一化_大小写空格缺P全收敛`() {
        assertEquals("720P", HanimeResolution.normalizeLabel("720p"))
        assertEquals("720P", HanimeResolution.normalizeLabel(" 720P "))
        assertEquals("720P", HanimeResolution.normalizeLabel("720"))
        assertEquals("1080P", HanimeResolution.normalizeLabel("1080p"))
        assertNull(HanimeResolution.normalizeLabel(null))
        assertNull(HanimeResolution.normalizeLabel(""))
        assertNull(HanimeResolution.normalizeLabel("P"))
    }

    @Test
    fun `标准四档进固定槽保序`() {
        val r = HanimeResolution()
        r.parseResolution("720p", "https://cdn/x720.mp4", "video/mp4")
        r.parseResolution("1080P", "https://cdn/x1080.mp4", "video/mp4")
        val map = r.toResolutionLinkMap()
        assertEquals(listOf("1080P", "720P"), map.keys.toList())
    }

    @Test
    fun `未知数字档不丢_进extras`() {
        val r = HanimeResolution()
        r.parseResolution("1080P", "https://cdn/a.mp4", "video/mp4")
        r.parseResolution("2160P", "https://cdn/b.mp4", "video/mp4")
        r.parseResolution("4K", "https://cdn/c.mp4", "video/mp4")
        val map = r.toResolutionLinkMap()
        assertNotNull(map["1080P"])
        // 2160P 是数字档：承认但不占固定槽。
        assertNotNull(map["2160P"])
        // 4K 非数字+P：落 Unknown 系，不丢。
        assertTrue(map.keys.any { it.startsWith("Unknown") })
        assertEquals(3, map.size)
    }

    @Test
    fun `空标签进Unknown_重复Unknown进extras不覆盖`() {
        val r = HanimeResolution()
        r.parseResolution(null, "https://cdn/a.mp4", "video/mp4")
        r.parseResolution("", "https://cdn/b.mp4", "video/mp4")
        val map = r.toResolutionLinkMap()
        assertTrue(map.keys.any { it == "Unknown" })
        assertEquals(2, map.size)
    }

    @Test
    fun `HLS顶层保留m3u8_后缀可判`() {
        val r = HanimeResolution()
        r.parseResolution("1080P", "https://cdn/x.m3u8", "application/x-mpegURL")
        val map = r.toResolutionLinkMap()
        val link = map["1080P"]
        assertNotNull(link)
        assertEquals("m3u8", link.subtype)
        assertEquals("m3u8", link.suffix)
    }

    @Test
    fun `空src由调用方过滤_此处不断言网络`() {
        // Parser 侧已过滤空 src（见 hanimeVideoVer2）；此处只保证 type 大小写不敏感。
        val r = HanimeResolution()
        r.parseResolution("720P", "https://cdn/x.mp4", "Video/MP4")
        assertEquals("mp4", r.toResolutionLinkMap()["720P"]?.suffix)
    }
}
