package lovehan1me.feature.account

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 头像裁剪的**纯几何换算**（无 UI / 平台依赖，便于单元测试）。
 *
 * 坐标约定（**cover 基线**）：
 * - 视口：正方形，边长 [viewport]（像素）
 * - 图片按 `fit = V / min(W,H)` **铺满**视口（短边贴边、长边溢出）并居中，
 *   再乘用户缩放 [scale]（≥1），最后平移 (dx, dy)
 * - 裁剪区 = 整个视口；因图片恒覆盖视口，换算回源图**必为正方形**且不越界
 *
 * ⚠️ 不能用 Fit 基线：横图在 scale=1 时竖向填不满视口，
 * 视口映射到源图上就不是正方形（会被夹成矩形/半块），所见非所得。
 */
internal object AvatarCropMath {

    /** 源图像素坐标下的正方形裁剪区。 */
    data class Rect(val x: Int, val y: Int, val size: Int)

    fun cropRect(
        srcW: Int,
        srcH: Int,
        viewport: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ): Rect {
        val size = min(srcW, srcH)
        if (srcW <= 0 || srcH <= 0 || viewport <= 0f) {
            // 尚未量到视口 / 非法输入：给一个零尺寸占位（调用方据此跳过裁剪）
            return Rect(0, 0, 0)
        }
        val fit = viewport / min(srcW, srcH).toFloat()
        val total = fit * scale
        val side = (viewport / total).roundToInt().coerceIn(1, size)
        // 图片左上角在视口坐标系里的位置（cover 下必有一维为 0、另一维 ≤ 0）
        val imgLeft = (viewport - srcW * total) / 2f + offsetX
        val imgTop = (viewport - srcH * total) / 2f + offsetY
        // 视口左上角 (0,0) 对应的源图坐标 = -imgLeft / total
        val x = (-imgLeft / total).roundToInt().coerceIn(0, max(0, srcW - side))
        val y = (-imgTop / total).roundToInt().coerceIn(0, max(0, srcH - side))
        return Rect(x, y, side)
    }

    /** 把位移夹取到「图片恒覆盖视口」的合法范围（返回 dx to dy）。 */
    fun clampOffset(
        srcW: Int,
        srcH: Int,
        viewport: Float,
        scale: Float,
        dx: Float,
        dy: Float,
    ): Pair<Float, Float> {
        if (viewport <= 0f || srcW <= 0 || srcH <= 0) return 0f to 0f
        val fit = viewport / min(srcW, srcH).toFloat()
        val maxX = max(0f, (srcW * fit * scale - viewport) / 2f)
        val maxY = max(0f, (srcH * fit * scale - viewport) / 2f)
        return dx.coerceIn(-maxX, maxX) to dy.coerceIn(-maxY, maxY)
    }
}
