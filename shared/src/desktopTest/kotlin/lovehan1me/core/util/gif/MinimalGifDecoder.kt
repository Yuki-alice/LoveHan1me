package lovehan1me.core.util.gif

import okio.Buffer

/**
 * 测试专用的**极简 GIF 解码器**（M3-b）。
 *
 * 存在的理由：GIF 的坑几乎全在 LZW 的位序（LSB first）与码长递增时机上，
 * 肉眼完全看不出来，只有"编码 → 解码 → 逐像素比对"的往返测试能证明。
 * 原作者在 `Gif89aEncoderTest` 里写了这份解码器，这里把它抽出来共用，
 * 避免中位切分调色板的测试再抄一遍（两份解码器会各自漂移）。
 *
 * ⚠️ 它**只覆盖本编码器产出的子集**：全局颜色表、无透明色、非隔行、无局部颜色表。
 * 它不是通用 GIF 解码器，也不该被拿去解外部 GIF。
 */
object MinimalGifDecoder {

    /** 解出所有帧的**调色板索引**（不是 ARGB —— 调用方自己按调色板还原）。 */
    fun decodeFrames(bytes: ByteArray): List<IntArray> {
        val frames = mutableListOf<IntArray>()
        var p = 6
        p += 4 // width(2) + height(2)
        val packed = bytes[p].toInt() and 0xFF
        p += 3 // packed + bgColorIndex + pixelAspectRatio
        if (packed and 0x80 != 0) {
            p += (1 shl ((packed and 7) + 1)) * 3
        }
        while (p < bytes.size) {
            when (bytes[p].toInt() and 0xFF) {
                0x21 -> { // Extension
                    p += 2
                    while (true) {
                        val len = bytes[p].toInt() and 0xFF
                        if (len == 0) {
                            p++
                            break
                        }
                        p += 1 + len
                    }
                }

                0x2C -> { // Image Descriptor
                    val w = bytes.readShortLe(p + 5)
                    val h = bytes.readShortLe(p + 7)
                    p += 10
                    val minCodeSize = bytes[p].toInt() and 0xFF
                    p += 1
                    val data = Buffer()
                    while (true) {
                        val len = bytes[p].toInt() and 0xFF
                        if (len == 0) {
                            p++
                            break
                        }
                        data.write(bytes, p + 1, len)
                        p += 1 + len
                    }
                    frames.add(lzwDecode(data.readByteArray(), minCodeSize, w * h))
                }

                0x3B -> return frames
                else -> error("未知块 0x${(bytes[p].toInt() and 0xFF).toString(16)} @$p")
            }
        }
        return frames
    }

    /** 读出全局颜色表（ARGB，alpha 恒 0xFF），按实际表项数返回。 */
    fun readGlobalColorTable(bytes: ByteArray): IntArray {
        val packed = bytes[10].toInt() and 0xFF
        check(packed and 0x80 != 0) { "无全局颜色表" }
        val entries = 1 shl ((packed and 7) + 1)
        val out = IntArray(entries)
        var p = 13
        for (i in 0 until entries) {
            val r = bytes[p].toInt() and 0xFF
            val g = bytes[p + 1].toInt() and 0xFF
            val b = bytes[p + 2].toInt() and 0xFF
            out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            p += 3
        }
        return out
    }

    /** 读出每帧的显示间隔（10ms 单位）。 */
    fun readDelays(bytes: ByteArray): List<Int> {
        val delays = mutableListOf<Int>()
        var p = 13
        val packed = bytes[10].toInt() and 0xFF
        if (packed and 0x80 != 0) p += (1 shl ((packed and 7) + 1)) * 3
        while (p < bytes.size) {
            when (bytes[p].toInt() and 0xFF) {
                0x21 -> {
                    val label = bytes[p + 1].toInt() and 0xFF
                    if (label == 0xF9) {
                        delays.add(bytes.readShortLe(p + 4))
                    }
                    p += 2
                    while (true) {
                        val len = bytes[p].toInt() and 0xFF
                        if (len == 0) {
                            p++
                            break
                        }
                        p += 1 + len
                    }
                }

                0x2C -> {
                    p += 10
                    p += 1
                    while (true) {
                        val len = bytes[p].toInt() and 0xFF
                        if (len == 0) {
                            p++
                            break
                        }
                        p += 1 + len
                    }
                }

                0x3B -> return delays
                else -> return delays
            }
        }
        return delays
    }

    /**
     * GIF 变体 LZW 解码。
     *
     * 注意「解码端字典比编码端小 1」：clear 之后的第一个 code 不入字典，
     * 因此**增位阈值也小 1**（判定用 `dict.size + 1 >= (1 shl codeSize)`）。
     * 写成 `dict.size == (1 shl codeSize)` 会晚一次增位，
     * 表现为第 255 个 code 起位流失步、`dict[code]` 越界。
     */
    private fun lzwDecode(data: ByteArray, minCodeSize: Int, expected: Int): IntArray {
        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1
        val out = ArrayList<Int>(expected)
        var bitPos = 0

        fun readCode(size: Int): Int {
            var v = 0
            for (i in 0 until size) {
                val bit = (data[bitPos ushr 3].toInt() ushr (bitPos and 7)) and 1
                v = v or (bit shl i)
                bitPos++
            }
            return v
        }

        val dict = ArrayList<IntArray>()
        var codeSize = minCodeSize + 1

        fun resetDict() {
            dict.clear()
            for (i in 0 until clearCode) dict.add(intArrayOf(i))
            dict.add(intArrayOf()) // CLEAR
            dict.add(intArrayOf()) // END
            codeSize = minCodeSize + 1
        }
        resetDict()

        var prev: Int? = null
        while (bitPos + codeSize <= data.size * 8) {
            val code = readCode(codeSize)
            if (code == clearCode) {
                resetDict()
                prev = null
                continue
            }
            if (code == endCode) break

            val entry: IntArray
            when {
                prev == null -> entry = dict[code]
                code < dict.size -> {
                    entry = dict[code]
                    dict.add(dict[prev] + entry[0])
                    if (dict.size + 1 >= (1 shl codeSize) && codeSize < 12) codeSize++
                }

                else -> {
                    val p = dict[prev]
                    entry = p + p[0]
                    dict.add(entry)
                    if (dict.size + 1 >= (1 shl codeSize) && codeSize < 12) codeSize++
                }
            }
            for (v in entry) out.add(v)
            prev = code
        }
        return out.toIntArray()
    }
}

/** 小端读 16 位（GIF 全部多字节字段都是小端）。 */
internal fun ByteArray.readShortLe(off: Int): Int =
    (this[off].toInt() and 0xFF) or ((this[off + 1].toInt() and 0xFF) shl 8)
