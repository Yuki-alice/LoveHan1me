package lovehan1me.video.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 画面比例的**单所有权**与黑边决策（D9）。
 *
 * 此前调用方与 UI 各写一份 `16/9` 兜底（比例一变，引擎每次上报尺寸都像换了部片子）；
 * 现在调用方一律上报 `0f`，兜底只此一份，黑边判定也收在这两个纯函数里。
 */
class VideoGeometryTest {

    @Test
    fun `D9 未上报时用兜底比例`() {
        assertEquals(DEFAULT_VIDEO_ASPECT_RATIO, resolveVideoAspectRatio(0f))
        assertEquals(DEFAULT_VIDEO_ASPECT_RATIO, resolveVideoAspectRatio(-1f))
    }

    @Test
    fun `D9 已上报时原样使用`() {
        assertEquals(4f / 3f, resolveVideoAspectRatio(4f / 3f))
    }

    @Test
    fun `D9 宽画面按宽撑满_窄画面按高撑满`() {
        // 画面比容器更宽 → 宽度撑满、上下留黑边
        assertTrue(videoFillsWidth(reported = 21f / 9f, containerAspectRatio = 16f / 9f))
        // 画面比容器更窄 → 高度撑满、左右留黑边
        assertFalse(videoFillsWidth(reported = 4f / 3f, containerAspectRatio = 16f / 9f))
    }

    @Test
    fun `D9 未上报时按兜底比例参与黑边决策`() {
        // 兜底 16:9 对上一个 4:3 的容器 → 按宽度撑满
        assertTrue(videoFillsWidth(reported = 0f, containerAspectRatio = 4f / 3f))
    }
}