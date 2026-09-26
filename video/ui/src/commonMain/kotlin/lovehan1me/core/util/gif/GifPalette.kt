package lovehan1me.core.util.gif

/**
 * GIF 全局颜色表的抽象（M3-b）。
 *
 * 存在的意义：把 [Gif89aEncoder] 从"写死 3:3:2 量化"里解耦出来。
 * 编码器只负责**容器 + LZW**，颜色怎么来由调色板决定 ——
 * 于是 3:3:2（快、够用的兜底）与中位切分（慢、画质好）可以互换，且都能被单测覆盖。
 *
 * 约定：
 * - `size` 必须 ≤ 256（GIF 颜色表上限）；
 * - `indexOf` 必须返回 `0 until size` 内的值，且对同一输入稳定（编码器会高频调用，
 *   实现内部应当自带缓存 —— 见 [MedianCutPalette] 的 5:5:5 查找表）；
 * - 颜色表的**项数**由编码器补齐到 2 的幂后写入（GIF 规范要求），
 *   所以 [colorAt] 只需覆盖 `0 until size`，多余的项编码器用 0 填充。
 */
interface GifPalette {
    /** 调色板项数，1..256。 */
    val size: Int

    /** 第 [index] 项的颜色（0xAARRGGBB；GIF 无 alpha，仅取 RGB 三个字节）。 */
    fun colorAt(index: Int): Int

    /** 把任意 ARGB 映射到最接近的调色板索引。 */
    fun indexOf(argb: Int): Int
}

/**
 * 固定 **3:3:2** 量化调色板（R 3bit / G 3bit / B 2bit = 256 色）。
 *
 * 这是 spike 阶段的实现，也是**任何情况下都能用的兜底**：
 * 零采样、零排序、O(1) 映射，代价是渐变色带明显。
 * 中位切分失败（例如帧数过少或内存紧张）时，编码管线会回退到它。
 *
 * 与 [Gif89aEncoder.quantize] / [Gif89aEncoder.paletteColor] 的映射完全一致
 * （这两个函数是历史 API，保留以兼容既有测试与调用方）。
 */
object Fixed332Palette : GifPalette {
    override val size: Int = 256

    override fun colorAt(index: Int): Int {
        val rgb = Gif89aEncoder.paletteColor(index)
        return (0xFF shl 24) or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
    }

    override fun indexOf(argb: Int): Int = Gif89aEncoder.quantize(argb)
}
