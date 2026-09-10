package me.lovehan1me.ui.screen.home.preview.getchupreview

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import me.lovehan1me.ui.screen.home.dailycheckin.today
import me.lovehan1me.ui.screen.home.dailycheckin.ymCode

// P6d-2：从 :app 下沉（包名不变）。日期函数重写（kotlinx 对齐）；
// ImageRequest 的 LocalContext → coil3 跨平台 LocalPlatformContext（Android 行为一致）；
// rememberGetchuImageLoader 为 expect（jvm 真实现 / ios 默认实现，见平台文件）。

/**
 * 获取当前月份对应的日期码。
 *
 * @return 本月日期码字符串
 */
fun currentGetchuDateCode(): String {
    val now = today()
    return ymCode(now.year, now.monthNumber)
}

/**
 * 将日期码转换为可读的标签格式（yyyy/MM）。
 *
 * @param code 日期码，如 "202401"
 * @return 标签字符串，如 "2024/1"
 */
fun getchuDateLabel(code: String): String {
    return "${code.substring(0, 4)}/${code.substring(4, 6).toInt()}"
}

/**
 * 将日期码按指定偏移量移动月份。
 *
 * @param code 日期码，如 "202401"
 * @param delta 偏移月数（负数表示向前）
 * @return 新日期码
 */
fun shiftGetchuMonthCode(code: String, delta: Int): String {
    var year = code.substring(0, 4).toInt()
    var month = code.substring(4, 6).toInt() + delta
    while (month < 1) {
        month += 12
        year -= 1
    }
    while (month > 12) {
        month -= 12
        year += 1
    }
    return ymCode(year, month)
}

fun getchuMonthOptions(centerCode: String): List<String> {
    return (-12..12).map { delta -> shiftGetchuMonthCode(centerCode, delta) }
}

@Composable
fun getchuImageRequest(url: String?): ImageRequest {
    val context = LocalPlatformContext.current
    return ImageRequest.Builder(context)
        .data(url)
        .build()
}

/**
 * getchu 域名特化的图片加载器（OkHttp + HDns/代理/UA/Referer/Cookie）。
 * jvmMain 真实现；iosMain 为默认 ImageLoader（TODO P7 getchu 特化）。
 */
@Composable
expect fun rememberGetchuImageLoader(): ImageLoader
