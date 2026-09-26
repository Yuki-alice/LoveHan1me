package lovehan1me.video.contract

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 超分档位的读侧回落（T10 的一半）。
 *
 * 落盘的档位要在重进播放页时先收敛到**当前引擎真的可选**的档位再下发：
 * 老存档没有这个键（读到默认 OFF）、存档值超出当前引擎可选档位（换了引擎）、
 * 存档被写坏（负数 / 越界）都回落到 OFF，而不是把无效索引交给引擎。
 */
class VideoEnhancementControllerTest {

    @Test
    fun `T10 落盘档位在可选档位内时原样下发`() {
        assertEquals(
            VideoEnhancementLevels.QUALITY,
            resolveEnhancementLevel(VideoEnhancementLevels.QUALITY, VideoEnhancementLevels.ALL),
        )
    }

    @Test
    fun `T10 存档档位超出当前引擎可选档位时回落到 OFF`() {
        // 换到只支持 OFF/PERFORMANCE 的引擎后，存档里的 QUALITY 不能直接下发。
        assertEquals(
            VideoEnhancementLevels.OFF,
            resolveEnhancementLevel(
                VideoEnhancementLevels.QUALITY,
                listOf(VideoEnhancementLevels.OFF, VideoEnhancementLevels.PERFORMANCE),
            ),
        )
    }

    @Test
    fun `T10 老存档缺键与写坏的档位都回落到 OFF`() {
        assertEquals(VideoEnhancementLevels.OFF, resolveEnhancementLevel(0, VideoEnhancementLevels.ALL))
        assertEquals(VideoEnhancementLevels.OFF, resolveEnhancementLevel(-1, VideoEnhancementLevels.ALL))
        assertEquals(VideoEnhancementLevels.OFF, resolveEnhancementLevel(99, VideoEnhancementLevels.ALL))
    }
}