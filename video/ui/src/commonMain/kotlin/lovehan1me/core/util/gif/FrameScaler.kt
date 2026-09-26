package lovehan1me.core.util.gif

/**
 * 纯 Kotlin 的帧缩放（M3-b 录制管线用）。
 *
 * 为什么不用最近邻：视频降采样时最近邻会**直接丢弃采样点之间的像素**，
 * 在动漫的细线条和文字上产生明显锯齿/闪烁；面积平均（box filter）把每个目标像素
 * 对应的源矩形做平均，代价相同（每个源像素只被读一次），观感好得多。
 *
 * 为什么不用 Skia/Bitmap：本函数要在 **commonMain** 被桌面与 Android 共用，
 * 而两端的图像缩放 API 并不通用；纯整数运算反而可被单测逐像素断言。
 *
 * 约定：只处理 0xAARRGGBB 的 `IntArray`；alpha 不参与平均（GIF 无透明），
 * 输出 alpha 恒为 0xFF。
 */
object FrameScaler {

    /**
     * 把 [source]（`srcW x srcH`）缩放到 `dstW x dstH`。
     *
     * - 尺寸相同：返回副本（不改调用方的数组）；
     * - 缩小：面积平均；
     * - 放大：最近邻（[GifCapturePolicy] 不会放大，这里只为健壮性兜底）。
     *
     * @throws IllegalArgumentException 源数组长度与尺寸不符，或目标尺寸非正
     */
    fun scale(source: IntArray, srcW: Int, srcH: Int, dstW: Int, dstH: Int): IntArray {
        require(srcW > 0 && srcH > 0) { "源尺寸必须为正：${srcW}x$srcH" }
        require(dstW > 0 && dstH > 0) { "目标尺寸必须为正：${dstW}x$dstH" }
        require(source.size == srcW * srcH) {
            "源像素数 ${source.size} 与 ${srcW}x$srcH 不符"
        }
        if (dstW == srcW && dstH == srcH) return source.copyOf()
        if (dstW > srcW || dstH > srcH) return nearest(source, srcW, srcH, dstW, dstH)
        return boxAverage(source, srcW, srcH, dstW, dstH)
    }

    private fun boxAverage(
        source: IntArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
    ): IntArray {
        val out = IntArray(dstW * dstH)
        for (dy in 0 until dstH) {
            // 目标像素 dy 覆盖的源行区间 [y0, y1)
            val y0 = dy * srcH / dstH
            val y1 = (((dy + 1) * srcH) / dstH).coerceAtLeast(y0 + 1).coerceAtMost(srcH)
            for (dx in 0 until dstW) {
                val x0 = dx * srcW / dstW
                val x1 = (((dx + 1) * srcW) / dstW).coerceAtLeast(x0 + 1).coerceAtMost(srcW)

                var r = 0L
                var g = 0L
                var b = 0L
                var n = 0
                for (y in y0 until y1) {
                    val row = y * srcW
                    for (x in x0 until x1) {
                        val p = source[row + x]
                        r += (p shr 16) and 0xFF
                        g += (p shr 8) and 0xFF
                        b += p and 0xFF
                        n++
                    }
                }
                // n 恒 >= 1：上面的区间都至少 1 像素宽/高
                out[dy * dstW + dx] = (0xFF shl 24) or
                    (((r / n).toInt()) shl 16) or
                    (((g / n).toInt()) shl 8) or
                    ((b / n).toInt())
            }
        }
        return out
    }

    private fun nearest(
        source: IntArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
    ): IntArray {
        val out = IntArray(dstW * dstH)
        for (dy in 0 until dstH) {
            val sy = (dy.toLong() * srcH / dstH).toInt().coerceIn(0, srcH - 1)
            val row = sy * srcW
            for (dx in 0 until dstW) {
                val sx = (dx.toLong() * srcW / dstW).toInt().coerceIn(0, srcW - 1)
                out[dy * dstW + dx] = source[row + sx]
            }
        }
        return out
    }
}
