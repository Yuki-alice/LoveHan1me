package lovehan1me.core.util

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * shader 落盘的端到端测试：真实走 composeResources → 平台目录 → 文件校验。
 */
class MpvShadersDesktopTest {

    @Test
    fun `OFF 档返回空串`() = runBlocking {
        assertEquals("", materializeMpvShaders(MpvShaders.OFF))
    }

    @Test
    fun `QUALITY 档落盘 5 个 shader 并返回路径`() = runBlocking {
        val paths = materializeMpvShaders(MpvShaders.QUALITY)
        assertNotNull(paths, "落盘不应失败（composeResources 里的 shader 应可读取）")
        val files = paths.split(File.pathSeparator).map(::File)
        assertEquals(MpvShaders.fileNames(MpvShaders.QUALITY).size, files.size, "5 个 shader")
        files.forEach { f ->
            assertTrue(f.exists(), "${f.name} 应已落盘")
            assertTrue(f.length() > 0, "${f.name} 不应为空")
        }
    }

    @Test
    fun `PERFORMANCE 档落盘 3 个 shader`() = runBlocking {
        val paths = materializeMpvShaders(MpvShaders.PERFORMANCE)
        assertNotNull(paths)
        assertEquals(3, paths.split(File.pathSeparator).size)
    }

    @Test
    fun `重复调用幂等_不报错`() = runBlocking {
        val a = materializeMpvShaders(MpvShaders.QUALITY)
        val b = materializeMpvShaders(MpvShaders.QUALITY)
        assertEquals(a, b, "幂等：二次调用返回相同路径")
    }
}
