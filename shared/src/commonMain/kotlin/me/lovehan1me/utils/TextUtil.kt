package me.lovehan1me.utils

import androidx.annotation.IntRange
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * P6d-4：自上游 utils 库 TextUtil.kt 内联（该库为 Android-only，无 KMP 坐标）。
 * 包名与函数签名保持不变，:app / shared 调用点零改动。
 */

@OptIn(ExperimentalEncodingApi::class)
fun String.decodeFromStringByBase64(): String {
    // 原 android.util.Base64.DEFAULT；Mime 解码对换行等空白更宽容，正常输入结果一致
    return Base64.Mime.decode(this).decodeToString()
}

private val SI_UNITS = arrayOf("B", "K", "M", "G", "T")
private val IEC_UNITS = arrayOf("B", "KiB", "MiB", "GiB", "TiB")

// 原 "%.Nf".format(Locale.getDefault())：commonMain 无 String.format，
// 以整数缩放实现 HALF_UP 舍入 + 定点拼接（对正数与 Formatter 行为一致）。
// 本文件输入恒为字节数换算值，非负。
private fun Double.toPlainFixed(places: Int): String {
    val factor = 10.0.pow(places)
    val scaled = (this * factor).roundToLong()
    val f = factor.toLong()
    val intPart = scaled / f
    val fracPart = scaled % f
    return if (places == 0) "$intPart" else "$intPart.${fracPart.toString().padStart(places, '0')}"
}

fun Long.formatFileSize(
    useSi: Boolean = true,
    @IntRange(from = 0) decimalPlaces: Int = 1,
    stripTrailingZeros: Boolean = true,
): String {
    val unit = if (useSi) 1000 else 1024
    if (this < unit) return "$this B"

    val units = if (useSi) SI_UNITS else IEC_UNITS
    var value = toDouble()
    var unitIndex = 0

    while (value >= unit && unitIndex < units.size - 1) {
        value /= unit
        unitIndex++
    }

    return if (decimalPlaces == 0 || (stripTrailingZeros && value % 1 == 0.0)) {
        "${value.toPlainFixed(0)} ${units[unitIndex]}"
    } else {
        "${value.toPlainFixed(decimalPlaces)} ${units[unitIndex]}"
    }
}

fun Long.formatBytesPerSecond(
    useSi: Boolean = true,
    @IntRange(from = 0) decimalPlaces: Int = 1,
    stripTrailingZeros: Boolean = true,
): String {
    return formatFileSize(useSi, decimalPlaces, stripTrailingZeros) + "/s"
}
