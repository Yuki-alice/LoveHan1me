package lovehan1me.core.platform

import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 桌面端头像像素管线的**端到端**测试：
 * 合成一张测试图 → [decodeAvatarSource] → [cropAndSaveAvatar] → 回读产物验证。
 *
 * 纯 JVM（java.awt + skiko），无需 GUI。
 */
class AvatarImageIoDesktopTest {

    private val tempDir = createTempDir("avatar-io-test")
    private val sourceFile: File by lazy { writeSyntheticImage() }

    private fun writeSyntheticImage(): File {
        // 1000×500 横图：左半红、右半蓝，中心放一个白块（用于确认裁剪位置取的是中心）
        val img = BufferedImage(1000, 500, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 1000) {
            for (y in 0 until 500) {
                val white = x in 400..599 && y in 150..349
                img.setRGB(x, y, when {
                    white -> 0xFFFFFF
                    x < 500 -> 0xFF0000
                    else -> 0x0000FF
                })
            }
        }
        val bos = ByteArrayOutputStream()
        ImageIO.write(img, "png", bos)
        val f = File(tempDir, "source.png")
        f.writeBytes(bos.toByteArray())
        return f
    }

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `解码源图_尺寸正确`() = runBlocking {
        val bmp = decodeAvatarSource(sourceFile.absolutePath)
        assertNotNull(bmp, "解码不应失败")
        assertEquals(1000, bmp.width)
        assertEquals(500, bmp.height)
    }

    @Test
    fun `超大图会被降采样到 maxPx`() = runBlocking {
        // 2000×1000，maxPx=1600 → 长边压到 1600
        val big = BufferedImage(2000, 1000, BufferedImage.TYPE_INT_RGB)
        val bos = ByteArrayOutputStream()
        ImageIO.write(big, "png", bos)
        val f = File(tempDir, "big.png").apply { writeBytes(bos.toByteArray()) }
        val bmp = decodeAvatarSource(f.absolutePath, maxPx = 1600)
        assertNotNull(bmp)
        assertEquals(1600, bmp.width)
        assertEquals(800, bmp.height)
    }

    @Test
    fun `不存在的文件返回null_不抛异常`() = runBlocking {
        val missing = File(tempDir, "no-such-file.png").absolutePath
        assertEquals(null, decodeAvatarSource(missing))
        assertEquals(
            null,
            cropAndSaveAvatar(missing, AvatarCropRect(0, 0, 100)),
        )
    }

    @Test
    fun `中心裁剪_产物为指定尺寸的正方形`() = runBlocking {
        // 中心 500×500 正方形（整图即中心正方形：x=250,y=0）
        val out = cropAndSaveAvatar(sourceFile.absolutePath, AvatarCropRect(250, 0, 500))
        assertNotNull(out, "裁剪落盘不应失败")
        val outBytes = File(out).readBytes()
        val bmp = decodeAvatarSource(out)
        assertNotNull(bmp)
        assertEquals(512, bmp.width, "默认 outputPx=512")
        assertEquals(512, bmp.height)
        assertTrue(outBytes.isNotEmpty())
    }

    @Test
    fun `裁剪矩形越界会被夹回图内`() = runBlocking {
        // 越界：x=-100, size=700 → 实际取 (0,0,500)
        val out = cropAndSaveAvatar(sourceFile.absolutePath, AvatarCropRect(-100, -50, 700))
        assertNotNull(out)
        val bmp = decodeAvatarSource(out)
        assertNotNull(bmp)
        assertEquals(512, bmp.width)
    }

    @Test
    fun `file协议路径也能解码`() = runBlocking {
        // assertNotNull 返回非空值，这里取出来顺便断言尺寸，
        // 保证测试方法末句是 Unit（JUnit4 要求 @Test 方法为 void）
        val bmp = assertNotNull(decodeAvatarSource(sourceFile.toURI().toString()), "file:// 前缀也应可解码")
        assertEquals(1000, bmp.width)
    }

    private fun createTempDir(prefix: String): File =
        File(System.getProperty("java.io.tmpdir"), "$prefix-${System.nanoTime()}").apply { mkdirs() }
}
