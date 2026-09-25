package lovehan1me.core.platform

import lovehan1me.data.database.entity.download.HanimeDownloadEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// DB 行 → 可播信息的映射测试（Gate4-4）。
// 文件存在性判定在各端 actual（java.io.File / NSFileManager），这里只钉纯映射：
// 与 Android `HanimeCacheManager` 实体兜底分支逐字段一致（info.json 那条只有 Android 走）。
class VideoCacheLookupTest {

    private fun entity() = HanimeDownloadEntity(
        coverUrl = "https://example.com/cover.jpg",
        coverUri = "/local/cover.jpg",
        title = "测试标题",
        addDate = 1L,
        videoCode = "12345",
        videoUri = "/local/12345/测试标题 [1080P].mp4",
        quality = "1080P",
        videoUrl = "https://example.com/v.mp4",
        length = 100L,
        downloadedLength = 100L,
    )

    @Test
    fun `实体映射出可播信息`() {
        val video = entity().toCachedVideo()
        assertEquals("测试标题", video.title)
        // 本地封面优先于远端
        assertEquals("/local/cover.jpg", video.coverUrl)
        assertTrue(video.videoUrls.containsKey("1080P"))
        assertEquals("/local/12345/测试标题 [1080P].mp4", video.videoUrls["1080P"]?.link)
        // 简介类元数据为空（与 Android 兜底一致，播放仅需标题/链接/封面）
        assertNull(video.introduction)
        assertTrue(video.tags.isEmpty())
    }

    @Test
    fun `无本地封面回落远端封面`() {
        val video = entity().copy(coverUri = null).toCachedVideo()
        assertEquals("https://example.com/cover.jpg", video.coverUrl)
    }
}
