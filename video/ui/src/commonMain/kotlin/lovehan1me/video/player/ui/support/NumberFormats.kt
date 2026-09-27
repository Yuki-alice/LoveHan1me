/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.support

import kotlin.math.round

/** 等价于 `String.format("%.2f", value)`：iOS 侧没有 `String.format`，只能用算术凑出两位小数。 */
private fun format2f(value: Float): String = (round(value * 100) / 100.0).toString()

/** 倍速文本：`1` → `"1.00"`，`1.25` → `"1.25"`，`2.1` → `"2.10"`。 */
fun Float.formatSpeedValue(): String {
    return format2f(this).let { it.padEnd(it.indexOf('.') + 3, '0') }
}

/** 左侧补 [prefix] 到 [length] 位，时间戳渲染用。 */
fun Int.fixToString(length: Int, prefix: Char = '0'): String = toString().padStart(length, prefix)

fun Long.fixToString(length: Int, prefix: Char = '0'): String = toString().padStart(length, prefix)
