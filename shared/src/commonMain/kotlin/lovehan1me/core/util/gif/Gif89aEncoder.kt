package lovehan1me.core.util.gif

import okio.Buffer

/**
 * 纯 Kotlin 的 GIF89a 编码器（M3-b「片段转 GIF」）。
 *
 * 设计约束（来自总体规划决策）：**不引入 ffmpeg-kit 或任何平台解码库**，
 * 因此这里是手写 GIF89a 容器 + GIF 变体 LZW 压缩。
 *
 * ## 能力边界
 * - 调色板由 [GifPalette] 决定：默认 [Fixed332Palette]（3:3:2，快），
 *   录制管线用 [MedianCutPalette]（中位切分，画质好）。
 * - 颜色表项数会**向上补齐到 2 的幂**（GIF 规范要求），补齐部分写 0；
 *   LZW 最小码长随之由 2..8 自适应，所以小调色板（如 64 色）能正常编码。
 * - 不支持透明色、不支持局部颜色表、不支持帧间差分（每帧全量写入）。
 * - 多帧通过 Netscape Application Extension 实现循环。
 *
 * 正确性由 `Gif89aEncoderTest` 的「编码 → 解码 → 逐像素比对」往返测试保证。
 */
object Gif89aEncoder {

    /**
     * 把若干帧 ARGB 位图编码为 GIF89a 字节流。
     *
     * @param width 宽（所有帧一致）
     * @param height 高（所有帧一致）
     * @param frames 每帧像素，0xAARRGGBB，长度必须为 `width * height`
     * @param delayCentis 每帧间隔，单位 10ms（GIF 规范单位）
     * @param loop 循环次数，0 表示无限循环
     * @param palette 全局颜色表来源，默认 3:3:2
     */
    fun encode(
        width: Int,
        height: Int,
        frames: List<IntArray>,
        delayCentis: Int,
        loop: Int = 0,
        palette: GifPalette = Fixed332Palette,
    ): ByteArray {
        require(frames.isNotEmpty()) { "至少需要一帧" }
        require(width > 0 && height > 0) { "尺寸必须为正：${width}x$height" }

        val tableEntries = tableEntriesFor(palette.size)
        val minCodeSize = log2(tableEntries)

        val buf = Buffer()
        buf.writeUtf8("GIF89a")
        writeLogicalScreenDescriptor(buf, width, height, minCodeSize)
        writeGlobalColorTable(buf, palette, tableEntries)

        if (frames.size > 1) {
            writeNetscapeLoopExtension(buf, loop)
        }

        val indices = IntArray(width * height)
        for (frame in frames) {
            require(frame.size == width * height) {
                "帧像素数 ${frame.size} 与 ${width}x${height} 不符"
            }
            for (i in frame.indices) indices[i] = palette.indexOf(frame[i])
            writeGraphicControlExtension(buf, delayCentis)
            writeImageDescriptor(buf, width, height)
            writeImageData(buf, indices, minCodeSize)
        }

        buf.writeByte(0x3B) // Trailer
        return buf.readByteArray()
    }

    /**
     * ARGB → 3:3:2 调色板索引（R 高 3 位 / G 高 3 位 / B 高 2 位）。
     *
     * 历史 API：既有的往返测试直接断言这个映射，故签名与语义保持不变。
     * 新代码请用 [GifPalette.indexOf]。
     */
    fun quantize(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return ((r shr 5) shl 5) or ((g shr 5) shl 2) or (b shr 6)
    }

    /**
     * 调色板索引 → RGB（3 字节）。量化是有损的，还原时按位宽均匀拉开到 0–255。
     */
    fun paletteColor(index: Int): IntArray {
        val r = (index shr 5) and 0x7
        val g = (index shr 2) and 0x7
        val b = index and 0x3
        return intArrayOf(r * 36, g * 36, b * 85)
    }

    /** 颜色表项数：向上补齐到 2 的幂，且不少于 2（GIF 规范下限）。 */
    private fun tableEntriesFor(colorCount: Int): Int {
        var entries = 2
        while (entries < colorCount && entries < 256) entries = entries shl 1
        return entries
    }

    private fun log2(v: Int): Int {
        var bits = 0
        var n = v
        while (n > 1) {
            n = n shr 1
            bits++
        }
        return bits.coerceIn(2, 8) // GIF 的 LZW 最小码长下限是 2
    }

    private fun writeLogicalScreenDescriptor(buf: Buffer, width: Int, height: Int, minCodeSize: Int) {
        buf.writeShortLe(width)
        buf.writeShortLe(height)
        // 0x80 全局颜色表标志 | 0x70 颜色分辨率 | (minCodeSize-1) 表大小 = 2^minCodeSize
        buf.writeByte(0x80 or 0x70 or (minCodeSize - 1))
        buf.writeByte(0) // 背景色索引
        buf.writeByte(0) // 像素宽高比（0 = 不指定）
    }

    private fun writeGlobalColorTable(buf: Buffer, palette: GifPalette, tableEntries: Int) {
        for (i in 0 until tableEntries) {
            if (i < palette.size) {
                val argb = palette.colorAt(i)
                buf.writeByte((argb shr 16) and 0xFF)
                buf.writeByte((argb shr 8) and 0xFF)
                buf.writeByte(argb and 0xFF)
            } else {
                // 补齐位：写黑，避免读取未初始化数据
                buf.writeByte(0); buf.writeByte(0); buf.writeByte(0)
            }
        }
    }

    private fun writeNetscapeLoopExtension(buf: Buffer, loop: Int) {
        buf.writeByte(0x21) // Extension Introducer
        buf.writeByte(0xFF) // Application Extension
        buf.writeByte(0x0B) // 块大小 11
        buf.writeUtf8("NETSCAPE2.0")
        buf.writeByte(0x03) // 子块大小
        buf.writeByte(0x01) // 子块 ID
        buf.writeShortLe(loop)
        buf.writeByte(0x00) // 块终结
    }

    private fun writeGraphicControlExtension(buf: Buffer, delayCentis: Int) {
        buf.writeByte(0x21)
        buf.writeByte(0xF9)
        buf.writeByte(0x04)
        // 处置方法 1（不处置）+ 无透明色
        buf.writeByte(0x04)
        buf.writeShortLe(delayCentis)
        buf.writeByte(0) // 透明色索引（未启用）
        buf.writeByte(0x00) // 块终结
    }

    private fun writeImageDescriptor(buf: Buffer, width: Int, height: Int) {
        buf.writeByte(0x2C)
        buf.writeShortLe(0) // left
        buf.writeShortLe(0) // top
        buf.writeShortLe(width)
        buf.writeShortLe(height)
        buf.writeByte(0x00) // 无局部颜色表、非隔行
    }

    private fun writeImageData(buf: Buffer, indices: IntArray, minCodeSize: Int) {
        buf.writeByte(minCodeSize)
        val lzw = Buffer()
        val writer = BitWriter(lzw)
        compressLzw(indices, writer, minCodeSize)
        writer.flush()
        writeSubBlocks(buf, lzw.readByteArray())
    }

    /**
     * GIF 变体 LZW。位序为 **LSB first**，码长随字典增长从 `minCodeSize+1` 递增到 12。
     *
     * 字典键用 `(prefix shl 8) or k` 编码成 Int，避免用字符串拼 key。
     */
    private fun compressLzw(indices: IntArray, writer: BitWriter, minCodeSize: Int) {
        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1
        val maxCode = 4095

        var codeSize = minCodeSize + 1
        var next = endCode + 1
        val dict = HashMap<Int, Int>(maxCode)

        writer.write(clearCode, codeSize)

        var prefix = indices[0]
        for (i in 1 until indices.size) {
            val k = indices[i] and 0xFF
            val key = (prefix shl 8) or k
            val existing = dict[key]
            if (existing != null) {
                prefix = existing
                continue
            }
            writer.write(prefix, codeSize)
            if (next <= maxCode) {
                dict[key] = next
                next++
                if (next == maxCode + 1) {
                    // 字典满：发 clear 重置（必须先按当前码长写出）
                    writer.write(clearCode, codeSize)
                    dict.clear()
                    codeSize = minCodeSize + 1
                    next = endCode + 1
                } else if (next > (1 shl codeSize) - 1 && codeSize < 12) {
                    codeSize++
                }
            }
            prefix = k
        }
        writer.write(prefix, codeSize)
        writer.write(endCode, codeSize)
    }

    private fun writeSubBlocks(buf: Buffer, data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val chunk = minOf(255, data.size - offset)
            buf.writeByte(chunk)
            buf.write(data, offset, chunk)
            offset += chunk
        }
        buf.writeByte(0x00)
    }

    /**
     * GIF 的位流是 LSB first：码的低位先落到字节的低位。
     */
    private class BitWriter(private val out: Buffer) {
        private var accumulator = 0
        private var bitCount = 0

        fun write(code: Int, size: Int) {
            accumulator = accumulator or (code shl bitCount)
            bitCount += size
            while (bitCount >= 8) {
                out.writeByte(accumulator and 0xFF)
                accumulator = accumulator ushr 8
                bitCount -= 8
            }
        }

        fun flush() {
            if (bitCount > 0) {
                out.writeByte(accumulator and 0xFF)
                accumulator = 0
                bitCount = 0
            }
        }
    }
}
