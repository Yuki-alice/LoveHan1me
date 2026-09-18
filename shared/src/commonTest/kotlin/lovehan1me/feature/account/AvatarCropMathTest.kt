package lovehan1me.feature.account

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 裁剪几何换算的单元测试（**cover 基线**，全部数值手算锚定）。
 *
 * 固定场景：视口 500×500。
 * - 源图 1000×500（横图）：fit = 500/min(1000,500) = 1.0 → 显示 1000×500，
 *   水平溢出 500、垂直贴边
 * - 源图 500×1000（竖图）：fit = 1.0 → 显示 500×1000，垂直溢出 500
 */
class AvatarCropMathTest {

    @Test
    fun `横图_scale1_裁剪区为水平居中的正方形`() {
        val r = AvatarCropMath.cropRect(1000, 500, 500f, scale = 1f, offsetX = 0f, offsetY = 0f)
        // side = 500/1.0 = 500；imgLeft = (500-1000)/2 = -250 → x = 250
        assertEquals(250, r.x)
        assertEquals(0, r.y)
        assertEquals(500, r.size)
    }

    @Test
    fun `竖图_scale1_裁剪区为垂直居中的正方形`() {
        val r = AvatarCropMath.cropRect(500, 1000, 500f, scale = 1f, offsetX = 0f, offsetY = 0f)
        // side = 500；imgTop = (500-1000)/2 = -250 → y = 250
        assertEquals(0, r.x)
        assertEquals(250, r.y)
        assertEquals(500, r.size)
    }

    @Test
    fun `放大后视口覆盖的源图区域变小`() {
        val at1 = AvatarCropMath.cropRect(1000, 500, 500f, scale = 1f, 0f, 0f)
        val at2 = AvatarCropMath.cropRect(1000, 500, 500f, scale = 2f, 0f, 0f)
        // scale=2: total=2, side=500/2=250
        // imgLeft=(500-2000)/2=-750 → x=750/2=375；imgTop=(500-1000)/2=-250 → y=250/2=125
        assertEquals(250, at2.size)
        assertEquals(375, at2.x)
        assertEquals(125, at2.y)
        assertTrue(at2.size < at1.size)
    }

    @Test
    fun `非法输入返回零尺寸_不抛异常`() {
        assertEquals(0, AvatarCropMath.cropRect(0, 0, 0f, 1f, 0f, 0f).size)
        assertEquals(0, AvatarCropMath.cropRect(-5, -5, -1f, 1f, 0f, 0f).size)
    }

    @Test
    fun `clampOffset_贴边方向位移归零_溢出方向保留`() {
        // 横图 1000×500 @ scale=1：水平溢出 500 → maxX=250；垂直贴边 → maxY=0
        val (dx, dy) = AvatarCropMath.clampOffset(1000, 500, 500f, 1f, dx = -50f, dy = -999f)
        assertEquals(-50f, dx)
        // 注意：coerceIn(-0.0, 0.0) 会返回 -0.0，Float.equals 区分正负零，
        // 故用 == 比较（语义上 -0.0 就是零，视觉无害）
        assertTrue(dy == 0f)
    }

    @Test
    fun `clampOffset_放大后两个方向都有余量`() {
        // scale=2：maxX=(2000-500)/2=750, maxY=(1000-500)/2=250
        val (dx, dy) = AvatarCropMath.clampOffset(1000, 500, 500f, 2f, dx = -100f, dy = -100f)
        assertEquals(-100f, dx)
        assertEquals(-100f, dy)
    }

    @Test
    fun `clampOffset_极端拖拽不会把图拖离视口`() {
        val (dx, dy) = AvatarCropMath.clampOffset(1000, 500, 500f, 2f, dx = -9999f, dy = 9999f)
        assertEquals(-750f, dx)
        assertEquals(250f, dy)
    }
}
