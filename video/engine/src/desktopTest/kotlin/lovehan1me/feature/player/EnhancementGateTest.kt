package lovehan1me.feature.player

import lovehan1me.core.util.MpvShaders
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 分辨率门控的判定。
 *
 * 这条判定曾是"按钮点了没反应"的成因：门控输入换成 mpv 的 `dwidth`/`dheight` 后，
 * 比值恒落在 1.0 附近（它报的就是视频自己的尺寸），于是任何档位都会被判成 OFF。
 * 现在只吃渲染面与片源尺寸两个数，就把这两种情况钉住：
 * 该省算力时关掉，尺寸没量到时**绝不**替用户关掉。
 */
class EnhancementGateTest {

    @Test
    fun `OFF 档不参与门控`() {
        assertEquals(
            MpvShaders.OFF,
            gateEnhancementByScale(MpvShaders.OFF, videoWidth = 480, videoHeight = 360, viewportWidth = 1920, viewportHeight = 1080),
        )
    }

    @Test
    fun `渲染面更大时保持请求档位`() {
        assertEquals(
            MpvShaders.QUALITY,
            gateEnhancementByScale(MpvShaders.QUALITY, videoWidth = 1280, videoHeight = 720, viewportWidth = 1920, viewportHeight = 1080),
        )
    }

    @Test
    fun `尺寸还没量到时不关掉用户显式打开的功能`() {
        listOf(
            0 to 720,   // 片源宽未知
            1280 to 0,  // 片源高未知
        ).forEach { (w, h) ->
            assertEquals(
                MpvShaders.PERFORMANCE,
                gateEnhancementByScale(MpvShaders.PERFORMANCE, videoWidth = w, videoHeight = h, viewportWidth = 1920, viewportHeight = 1080),
                "片源 ${w}x$h 时应保持请求档位",
            )
        }
        assertEquals(
            MpvShaders.PERFORMANCE,
            gateEnhancementByScale(MpvShaders.PERFORMANCE, videoWidth = 1280, videoHeight = 720, viewportWidth = 0, viewportHeight = 0),
            "渲染面还没量出来时应保持请求档位",
        )
    }

    @Test
    fun `渲染面等比或更小按 OFF 处理`() {
        assertEquals(
            MpvShaders.OFF,
            gateEnhancementByScale(MpvShaders.QUALITY, videoWidth = 1920, videoHeight = 1080, viewportWidth = 1920, viewportHeight = 1080),
            "1:1 没有放大可做",
        )
        assertEquals(
            MpvShaders.OFF,
            gateEnhancementByScale(MpvShaders.QUALITY, videoWidth = 1920, videoHeight = 1080, viewportWidth = 1280, viewportHeight = 720),
            "缩着放不该烧 GPU",
        )
    }

    @Test
    fun `两条边都要真放大才算需要超分`() {
        // 1920x1440 的渲染面对 1280x1440 的片源：宽够（1.5x），高刚好 1.0x ——
        // Anime4K 是等比放大链，单边够不等于整帧被放大，判 OFF。
        assertEquals(
            MpvShaders.OFF,
            gateEnhancementByScale(MpvShaders.QUALITY, videoWidth = 1280, videoHeight = 1440, viewportWidth = 1920, viewportHeight = 1440),
        )
        assertEquals(
            MpvShaders.QUALITY,
            gateEnhancementByScale(MpvShaders.QUALITY, videoWidth = 1280, videoHeight = 1400, viewportWidth = 1920, viewportHeight = 1440),
        )
    }
}
