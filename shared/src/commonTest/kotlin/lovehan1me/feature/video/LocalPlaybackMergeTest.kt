package lovehan1me.feature.video

import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.core.domain.model.HanimeVideo
import lovehan1me.core.domain.state.VideoLoadingState
import lovehan1me.core.platform.VideoCacheStore
import lovehan1me.site.hanime1.HanimeLink
import lovehan1me.site.hanime1.HanimeResolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 本地播放简介合并（VideoViewModel.mergeLocalPlaybackState）：
// 网络元数据全保留、直链锁定本地、系列/相关剥掉；离线回落缓存。
class LocalPlaybackMergeTest {

    private fun fakeStore(video: HanimeVideo?): VideoCacheStore = object : VideoCacheStore {
        override fun load(videoCode: String): Flow<HanimeVideo?> = flowOf(video)
    }

    private fun cachedVideo() = HanimeVideo(
        title = "站内原标题",
        coverUrl = "file:///local/cover.jpg",
        chineseTitle = null,
        introduction = null,
        uploadTime = null,
        videoUrls = linkedMapOf("720P" to HanimeLink("file:///local/12345/x [720P].mp4", "mp4")),
        tags = emptyList(),
    )

    private fun networkVideo() = HanimeVideo(
        title = "站内原标题",
        coverUrl = "https://example.com/cover.jpg",
        chineseTitle = "中文标题",
        introduction = "这是简介",
        uploadTime = LocalDate(2024, 1, 2),
        views = "12345",
        videoUrls = linkedMapOf("1080P" to HanimeLink("https://example.com/1080.mp4", "mp4")),
        tags = listOf("schoolgirl"),
        playlist = HanimeVideo.Playlist(
            playlistName = "系列名",
            video = listOf(
                HanimeInfo(
                    title = "第一集",
                    coverUrl = "https://example.com/1.jpg",
                    videoCode = "111",
                    itemType = HanimeInfo.NORMAL,
                )
            ),
        ),
        relatedHanimes = listOf(
            HanimeInfo(
                title = "相关片",
                coverUrl = "https://example.com/r.jpg",
                videoCode = "999",
                itemType = HanimeInfo.NORMAL,
            )
        ),
        artist = HanimeVideo.Artist(name = "作者", avatarUrl = "", genre = "genre"),
    )

    private fun vm() = VideoViewModel(fakeStore(cachedVideo()))

    @Test
    fun `网络到货后元数据补全且直链锁定本地`() {
        val merged = vm().mergeLocalPlaybackState(
            cached = cachedVideo(),
            netState = VideoLoadingState.Success(networkVideo()),
            playable = "file:///local/12345/x [720P].mp4",
        )
        assertIs<VideoLoadingState.Success<HanimeVideo>>(merged)
        val info = merged.info
        // 元数据来自网络
        assertEquals("中文标题", info.chineseTitle)
        assertEquals("这是简介", info.introduction)
        assertEquals(listOf("schoolgirl"), info.tags)
        assertEquals("作者", info.artist?.name)
        assertEquals(LocalDate(2024, 1, 2), info.uploadTime)
        assertEquals("12345", info.views)
        // 直链锁定本地真实清晰度（不是 1080P 单档覆盖）
        assertEquals(
            "file:///local/12345/x [720P].mp4",
            info.videoUrls["720P"]?.link,
        )
        assertTrue(info.videoUrls.none { it.value.link.startsWith("https://") })
        // 系列/相关剥掉
        assertNull(info.playlist)
        assertTrue(info.relatedHanimes.isEmpty())
        // 本地封面优先
        assertEquals("file:///local/cover.jpg", info.coverUrl)
    }

    @Test
    fun `无本地封面时用网络封面`() {
        val merged = vm().mergeLocalPlaybackState(
            cached = cachedVideo().copy(coverUrl = ""),
            netState = VideoLoadingState.Success(networkVideo()),
            playable = "file:///local/12345/x [720P].mp4",
        )
        assertIs<VideoLoadingState.Success<HanimeVideo>>(merged)
        assertEquals("https://example.com/cover.jpg", merged.info.coverUrl)
    }

    @Test
    fun `离线时回落缓存可播`() {
        val merged = vm().mergeLocalPlaybackState(
            cached = cachedVideo(),
            netState = VideoLoadingState.Error(RuntimeException("offline")),
            playable = "file:///local/12345/x [720P].mp4",
        )
        assertIs<VideoLoadingState.Success<HanimeVideo>>(merged)
        assertEquals(
            "file:///local/12345/x [720P].mp4",
            merged.info.videoUrls["720P"]?.link,
        )
        assertEquals("站内原标题", merged.info.title)
    }

    @Test
    fun `加载中先展缓存`() {
        val merged = vm().mergeLocalPlaybackState(
            cached = cachedVideo(),
            netState = VideoLoadingState.Loading,
            playable = "file:///local/12345/x [720P].mp4",
        )
        assertIs<VideoLoadingState.Success<HanimeVideo>>(merged)
    }

    @Test
    fun `缓存抖动时用单档本地兜底`() {
        val merged = vm().mergeLocalPlaybackState(
            cached = cachedVideo().copy(videoUrls = emptyMap()),
            netState = VideoLoadingState.Success(networkVideo()),
            playable = "file:///local/12345/x [720P].mp4",
        )
        assertIs<VideoLoadingState.Success<HanimeVideo>>(merged)
        assertEquals(
            "file:///local/12345/x [720P].mp4",
            merged.info.videoUrls[HanimeResolution.RES_1080P]?.link,
        )
        // 元数据仍来自网络
        assertEquals("中文标题", merged.info.chineseTitle)
        assertNull(merged.info.playlist)
    }
}
