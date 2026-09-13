package lovehan1me.core.util.gif

/**
 * **中位切分（median cut）调色板**（M3-b）。
 *
 * 路线图对 M3-b 的要求原本就是"纯 Kotlin GIF89a 编码 + 中位切分调色板"；
 * 上一版 spike 为了先证明"容器 + LZW 正确"，把调色板简化成了固定 3:3:2 量化。
 * 这里把画质补上：3:3:2 只有 8/8/4 个色阶，动漫画面的大面积暗部渐变会出色带，
 * 中位切分能按**画面实际的颜色分布**分配这 256 个槽位。
 *
 * ## 算法
 * 1. 把像素投到 **5:5:5 直方图**（32768 格）—— 不直接对像素做切分，
 *    否则 5s×12fps 的帧序列要排几千万个元素。
 * 2. 反复挑"某通道跨度最大"的盒子，沿该通道按**像素数中位**切成两半，
 *    直到盒子数达到 `maxColors` 或已无处可切。
 * 3. 每个盒子的代表色 = 盒内像素的加权平均（按格计数加权，用格中心还原 8bit）。
 * 4. 建 **5:5:5 → 索引查找表**：对每个格暴力找最近调色板色
 *    （32768 × 256 ≈ 840 万次距离计算，几十毫秒）。这样编码时 [indexOf] 是 O(1)，
 *    而且**空格子也有正确归属**——按盒子划分去查空格子会没有归属。
 *
 * @property size 实际调色板项数（≤ [maxColors]，首次调用方给定的上限）
 */
class MedianCutPalette private constructor(
    override val size: Int,
    private val colors: IntArray,
    private val lut: IntArray,
) : GifPalette {

    override fun colorAt(index: Int): Int = colors[index]

    override fun indexOf(argb: Int): Int = lut[binOf(argb)]

    companion object {
        private const val BITS = 5
        private const val LEVELS = 1 shl BITS // 32
        private const val BIN_COUNT = LEVELS * LEVELS * LEVELS // 32768

        /**
         * 默认采样上限。中位切分只需要"颜色分布"，不必看完所有像素；
         * 采样可把 500 万像素的代价压到 20 万，画质肉眼无差。
         */
        const val DEFAULT_MAX_SAMPLES = 200_000

        /** 把 ARGB 投到 5:5:5 格号。 */
        private fun binOf(argb: Int): Int =
            (((argb shr 19) and 0x1F) shl 10) or
                (((argb shr 11) and 0x1F) shl 5) or
                ((argb shr 3) and 0x1F)

        private fun r5(bin: Int) = (bin shr 10) and 0x1F
        private fun g5(bin: Int) = (bin shr 5) and 0x1F
        private fun b5(bin: Int) = bin and 0x1F

        /** 5bit 格中心还原成 8bit（0..255）：高位左移 3、再把高位补到低位。 */
        private fun expand(v: Int) = (v shl 3) or (v shr 2)

        /**
         * 从若干帧构建调色板。
         *
         * @param frames 每帧 0xAARRGGBB，长度应为 `w*h`；只用于统计，不会被修改
         * @param maxColors 目标色数上限（1..256）
         * @param maxSamples 统计采样上限，超了就等距抽样
         */
        fun build(
            frames: List<IntArray>,
            maxColors: Int = 256,
            maxSamples: Int = DEFAULT_MAX_SAMPLES,
        ): MedianCutPalette {
            val limit = maxColors.coerceIn(1, 256)
            val counts = LongArray(BIN_COUNT)
            val rSum = LongArray(BIN_COUNT)
            val gSum = LongArray(BIN_COUNT)
            val bSum = LongArray(BIN_COUNT)

            var totalPixels = 0L
            for (f in frames) totalPixels += f.size
            if (totalPixels == 0L) return single(0xFF000000.toInt())

            val stride = if (totalPixels <= maxSamples) 1L
            else (totalPixels / maxSamples).coerceAtLeast(1L)

            var seen = 0L
            for (f in frames) {
                for (px in f) {
                    // 等距抽样：跨帧连续计数，保证抽样在整段视频上均匀分布
                    if (stride > 1L && seen % stride != 0L) {
                        seen++
                        continue
                    }
                    seen++
                    val bin = binOf(px)
                    counts[bin]++
                    // ⚠️ 累加**像素的真实通道值**，不是格中心色。
                    // 累加格中心会把每个格子内的颜色"抹平到格心"，纯色区域也会被
                    // 量化偏（实测 0x336699 → 0x316299）。累加真实值后，
                    // 平坦区域（动漫里最常见）的盒子均值就是原色，可精确还原。
                    rSum[bin] += (px shr 16) and 0xFF
                    gSum[bin] += (px shr 8) and 0xFF
                    bSum[bin] += px and 0xFF
                }
            }

            // 收集非空格子
            var populated = 0
            for (i in 0 until BIN_COUNT) if (counts[i] != 0L) populated++
            if (populated == 0) return single(0xFF000000.toInt())

            val bins = IntArray(populated)
            var w = 0
            for (i in 0 until BIN_COUNT) if (counts[i] != 0L) bins[w++] = i

            val palette = if (populated <= limit) {
                // 颜色本来就少：每格一色，按出现次数降序（把高频色排前面）。
                // 用 List 排序而不是 IntArray.sortBy —— 后者在 primitive array 上
                // 并非所有 Kotlin 版本都提供，sortedByDescending 一定可用。
                val ordered = bins.toList().sortedByDescending { counts[it] }
                IntArray(ordered.size) { idx ->
                    val bin = ordered[idx]
                    argbOf(rSum[bin], gSum[bin], bSum[bin], counts[bin])
                }
            } else {
                medianCut(bins, counts, rSum, gSum, bSum, limit)
            }

            return MedianCutPalette(palette.size, palette, buildLut(palette))
        }

        private fun single(argb: Int) = MedianCutPalette(
            1,
            intArrayOf(argb),
            IntArray(BIN_COUNT) { 0 },
        )

        private fun argbOf(rSum: Long, gSum: Long, bSum: Long, count: Long): Int {
            if (count <= 0L) return 0xFF000000.toInt()
            val r = (rSum / count).toInt().coerceIn(0, 255)
            val g = (gSum / count).toInt().coerceIn(0, 255)
            val b = (bSum / count).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        /** 一个"待切分盒子"：`[from, to)` 是 bins 数组上的下标区间。 */
        private class Box(var from: Int, var to: Int) {
            var count = 0L
            var rSum = 0L
            var gSum = 0L
            var bSum = 0L
            var rMin = 0; var rMax = 0
            var gMin = 0; var gMax = 0
            var bMin = 0; var bMax = 0

            val rExtent get() = rMax - rMin
            val gExtent get() = gMax - gMin
            val bExtent get() = bMax - bMin

            /** 最长通道的跨度，用来挑"最该切"的盒子。 */
            val maxExtent get() = maxOf(rExtent, gExtent, bExtent)

            fun representative(): Int = argbOf(rSum, gSum, bSum, count)
        }

        private fun recompute(
            box: Box,
            bins: IntArray,
            counts: LongArray,
            rSum: LongArray,
            gSum: LongArray,
            bSum: LongArray,
        ) {
            var c = 0L; var rs = 0L; var gs = 0L; var bs = 0L
            var rlo = 31; var rhi = 0; var glo = 31; var ghi = 0; var blo = 31; var bhi = 0
            for (i in box.from until box.to) {
                val bin = bins[i]
                val n = counts[bin]
                c += n
                rs += rSum[bin]; gs += gSum[bin]; bs += bSum[bin]
                val r = r5(bin); val g = g5(bin); val b = b5(bin)
                if (r < rlo) rlo = r; if (r > rhi) rhi = r
                if (g < glo) glo = g; if (g > ghi) ghi = g
                if (b < blo) blo = b; if (b > bhi) bhi = b
            }
            box.count = c; box.rSum = rs; box.gSum = gs; box.bSum = bs
            box.rMin = rlo; box.rMax = rhi
            box.gMin = glo; box.gMax = ghi
            box.bMin = blo; box.bMax = bhi
        }

        private fun medianCut(
            bins: IntArray,
            counts: LongArray,
            rSum: LongArray,
            gSum: LongArray,
            bSum: LongArray,
            limit: Int,
        ): IntArray {
            val boxes = ArrayList<Box>(limit * 2)
            val first = Box(0, bins.size)
            recompute(first, bins, counts, rSum, gSum, bSum)
            boxes.add(first)

            // 复用的排序缓冲（按通道值打包成 Int，避免装箱比较器）
            val scratch = IntArray(bins.size)

            while (boxes.size < limit) {
                // 挑跨度最大、且还有像素可分的盒子
                var target = -1
                var best = 0
                for (i in boxes.indices) {
                    val b = boxes[i]
                    if (b.to - b.from < 2) continue
                    if (b.maxExtent > best) {
                        best = b.maxExtent
                        target = i
                    }
                }
                if (target < 0 || best == 0) break

                val box = boxes[target]
                val channel = when (best) {
                    box.rExtent -> 0
                    box.gExtent -> 1
                    else -> 2
                }
                val shift = when (channel) {
                    0 -> 10
                    1 -> 5
                    else -> 0
                }

                // 按该通道排序这个区间：打包成 (通道值 shl 15) or bin，
                // bin < 32768 = 2^15，故低位不会被污染，排序即按通道分组
                val n = box.to - box.from
                for (i in 0 until n) {
                    val bin = bins[box.from + i]
                    scratch[i] = (((bin shr shift) and 0x1F) shl 15) or bin
                }
                scratch.sort(0, n)
                for (i in 0 until n) bins[box.from + i] = scratch[i] and 0x7FFF

                // 按像素数找中位（**不是按下标中位** —— 那是"中位切分"的关键：
                // 让切出来的两半像素量相当，色阶才会按实际占比分配，而不是
                // 被一堆只有几个像素的孤立色占满槽位）
                val half = box.count / 2
                var acc = 0L
                // 初值保证左边至少 1 格；循环上界保证右边至少 1 格
                var split = box.from + 1
                for (i in box.from until box.to - 1) {
                    acc += counts[bins[i]]
                    if (acc >= half) {
                        split = i + 1
                        break
                    }
                }
                split = split.coerceIn(box.from + 1, box.to - 1)

                val right = Box(split, box.to)
                box.to = split
                recompute(box, bins, counts, rSum, gSum, bSum)
                recompute(right, bins, counts, rSum, gSum, bSum)
                boxes.add(right)
            }

            return IntArray(boxes.size) { boxes[it].representative() }
        }

        /**
         * 5:5:5 全空间 → 最近调色板索引。
         *
         * 暴力最近邻：32768 格 × 最多 256 色。**故意不做 k-d tree** ——
         * 这点固定开销（几十毫秒、一次性）换来实现简单且必然正确，
         * 比为一个"每次录制跑一次"的步骤引入复杂数据结构划算。
         */
        private fun buildLut(palette: IntArray): IntArray {
            val lut = IntArray(BIN_COUNT)
            val centers = IntArray(BIN_COUNT) { bin ->
                val r = expand(r5(bin)); val g = expand(g5(bin)); val b = expand(b5(bin))
                (r shl 16) or (g shl 8) or b
            }
            for (bin in 0 until BIN_COUNT) {
                val q = centers[bin]
                val qr = (q shr 16) and 0xFF
                val qg = (q shr 8) and 0xFF
                val qb = q and 0xFF
                var bestIndex = 0
                var bestDist = Int.MAX_VALUE
                for (p in palette.indices) {
                    val c = palette[p]
                    val dr = qr - ((c shr 16) and 0xFF)
                    val dg = qg - ((c shr 8) and 0xFF)
                    val db = qb - (c and 0xFF)
                    val d = dr * dr + dg * dg + db * db
                    if (d < bestDist) {
                        bestDist = d
                        bestIndex = p
                        if (d == 0) break
                    }
                }
                lut[bin] = bestIndex
            }
            return lut
        }
    }
}
