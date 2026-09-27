package lovehan1me.core.util

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * shader 落盘的端到端测试：真实走 composeResources → 平台目录 → 文件校验。
 *
 * 档位的 pass 链在这里钉死：顺序是"先压高光 → 还原 → 放大 → 收回 → 补细节"，
 * 写反了会把放大后的图再喂给还原链，且 mpv 侧看不出错、只有观感不对。
 */
class MpvShadersDesktopTest {

    @Test
    fun `OFF 档返回空表`() = runBlocking {
        assertEquals(emptyList(), materializeMpvShaders(MpvShaders.OFF))
    }

    @Test
    fun `QUALITY 档落盘 6 个 shader 并返回路径`() = runBlocking {
        val paths = materializeMpvShaders(MpvShaders.QUALITY)
        assertNotNull(paths, "落盘不应失败（composeResources 里的 shader 应可读取）")
        val files = paths.map(::File)
        assertEquals(
            listOf(
                "Anime4K_Clamp_Highlights.glsl",
                "Anime4K_Restore_CNN_VL.glsl",
                "Anime4K_Upscale_CNN_x2_VL.glsl",
                "Anime4K_AutoDownscalePre_x2.glsl",
                "Anime4K_AutoDownscalePre_x4.glsl",
                "Anime4K_Upscale_CNN_x2_M.glsl",
            ),
            files.map { it.name },
            "QUALITY 档的 pass 链顺序变了",
        )
        files.forEach { f ->
            assertTrue(f.exists(), "${f.name} 应已落盘")
            assertTrue(f.length() > 0, "${f.name} 不应为空")
        }
    }

    @Test
    fun `PERFORMANCE 档只有同尺寸还原`() = runBlocking {
        val paths = materializeMpvShaders(MpvShaders.PERFORMANCE)
        assertNotNull(paths)
        assertEquals(listOf("Anime4K_Restore_CNN_S.glsl"), paths.map { File(it).name })
    }

    @Test
    fun `每个路径都是绝对路径`() = runBlocking {
        // mpv 的 change-list 一次只吃一个路径，相对路径会按 mpv 的工作目录解析。
        listOf(MpvShaders.PERFORMANCE, MpvShaders.QUALITY).forEach { level ->
            materializeMpvShaders(level)?.forEach { path ->
                assertTrue(File(path).isAbsolute, "$path 必须是绝对路径")
            }
        }
    }

    @Test
    fun `重复调用幂等_不报错`() = runBlocking {
        val a = materializeMpvShaders(MpvShaders.QUALITY)
        val b = materializeMpvShaders(MpvShaders.QUALITY)
        assertEquals(a, b)
    }
}
