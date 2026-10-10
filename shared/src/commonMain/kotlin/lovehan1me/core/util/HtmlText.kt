package lovehan1me.core.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle

/**
 * P6d-4E：HTML → AnnotatedString（开源许可全文展示用）。
 * CMP 1.12 的 ui-text 无跨平台 fromHtml（实测 ui-desktop jar 0 命中），平台分治：
 * Android 用 androidx 实现；桌面/iOS 降级为去标签纯文本（许可正文主体可读）。
 */
expect fun parseHtmlToAnnotatedString(html: String): AnnotatedString

/** Android actual 里写死的链接蓝（非 @Composable 读不到主题，见 HtmlText.android.kt）。 */
val HtmlLinkFallbackColor = Color(0xFF1A73E8)

/**
 * 主题化 HTML：把解析结果里的 fallback 链接蓝换成当前主题 `primary`。
 *
 * 深色下 fallback 蓝对比度不足，直接用主题 primary 保证两套主题可读。
 * 非链接的前景色（保留的 ForegroundColorSpan）不动。
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun rememberThemedHtmlAnnotatedString(html: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val raw = remember(html) { parseHtmlToAnnotatedString(html) }
    return remember(raw, linkColor) {
        AnnotatedString.Builder(raw.text.length).apply {
            append(raw.text)
            raw.spanStyles.forEach { span ->
                val style: SpanStyle = if (span.item.color == HtmlLinkFallbackColor) {
                    span.item.copy(color = linkColor)
                } else {
                    span.item
                }
                addStyle(style, span.start, span.end)
            }
            raw.paragraphStyles.forEach { span ->
                addStyle(span.item, span.start, span.end)
            }
            raw.getStringAnnotations(0, raw.length).forEach { annotation ->
                addStringAnnotation(annotation.tag, annotation.item, annotation.start, annotation.end)
            }
            // 本版本链接以旧 `UrlAnnotation` 存读（`annotations` 内部不可见），
            // 按新 `LinkAnnotation.Url` 写回，保持可点击语义。
            raw.getUrlAnnotations(0, raw.length).forEach { annotation ->
                addLink(LinkAnnotation.Url(annotation.item.url), annotation.start, annotation.end)
            }
        }.toAnnotatedString()
    }
}
