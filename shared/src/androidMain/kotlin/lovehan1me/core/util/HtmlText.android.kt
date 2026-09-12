package lovehan1me.core.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat
import androidx.core.text.getSpans
import okio.sink
import okio.source

/**
 * P6d-4E：android actual——core-ktx parseAsHtml（CMP 1.12 内嵌的 androidx ui-text 无 fromHtml），
 * Spanned → AnnotatedString 简化转换：保留粗体/斜体/下划线/链接/前景色，其余样式忽略。
 */
actual fun parseHtmlToAnnotatedString(html: String): AnnotatedString {
    val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
    return buildAnnotatedString {
        append(spanned.toString())
        spanned.getSpans<android.text.style.StyleSpan>().forEach { span ->
            val start = spanned.getSpanStart(span); val end = spanned.getSpanEnd(span)
            when (span.style) {
                android.graphics.Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                android.graphics.Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                android.graphics.Typeface.BOLD_ITALIC -> addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
            }
        }
        spanned.getSpans<android.text.style.UnderlineSpan>().forEach { span ->
            addStyle(SpanStyle(textDecoration = TextDecoration.Underline), spanned.getSpanStart(span), spanned.getSpanEnd(span))
        }
        spanned.getSpans<android.text.style.URLSpan>().forEach { span ->
            val start = spanned.getSpanStart(span); val end = spanned.getSpanEnd(span)
            // 链接蓝刻意硬编码：本函数非 @Composable 读不到主题；蓝色链接是跨平台通用约定。
            addStyle(SpanStyle(color = androidx.compose.ui.graphics.Color(0xFF1A73E8), textDecoration = TextDecoration.Underline), start, end)
            addLink(LinkAnnotation.Url(span.url), start, end)
        }
        spanned.getSpans<android.text.style.ForegroundColorSpan>().forEach { span ->
            addStyle(SpanStyle(color = androidx.compose.ui.graphics.Color(span.foregroundColor)), spanned.getSpanStart(span), spanned.getSpanEnd(span))
        }
    }
}
