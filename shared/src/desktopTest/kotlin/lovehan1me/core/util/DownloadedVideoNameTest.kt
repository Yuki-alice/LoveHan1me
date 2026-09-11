package lovehan1me.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 桌面端已下载视频文件名反解的单元测试。
 * 落盘约定：`<sanitize(title)> [<quality>].<suffix>`
 */
class DownloadedVideoNameTest {

    @Test
    fun `标准命名可反解`() {
        assertEquals("标题" to "1080p", DownloadedVideoName.parse("标题 [1080p].mp4"))
        assertEquals("My Video" to "720p", DownloadedVideoName.parse("My Video [720p].webm"))
    }

    @Test
    fun `标题本身含中括号时取最后一个`() {
        // 标题里的 "[" 被sanitize保留，quality 取最后一个 "[...]"
        assertEquals("a [b" to "1080p", DownloadedVideoName.parse("a [b [1080p].mp4"))
    }

    @Test
    fun `缺后缀点号_返回null`() {
        assertNull(DownloadedVideoName.parse("标题 [1080p]"))
    }

    @Test
    fun `缺质量段_返回null`() {
        assertNull(DownloadedVideoName.parse("标题.mp4"))
        assertNull(DownloadedVideoName.parse("标题 [1080p_no_close.mp4"))
    }

    @Test
    fun `质量为空_返回null`() {
        assertNull(DownloadedVideoName.parse("标题 [].mp4"))
        assertNull(DownloadedVideoName.parse("标题 [ ].mp4"))
    }

    @Test
    fun `只有点号_返回null`() {
        assertNull(DownloadedVideoName.parse(".mp4"))
    }
}
