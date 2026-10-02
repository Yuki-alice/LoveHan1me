package lovehan1me.ui.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

/**
 * 把纯文本里的 URL 渲染成可点链接，其余部分原样输出。
 *
 * 从 `Announcement.getFormatedContent()` 搬过来的：那段逻辑原本挂在领域模型上，
 * 让 `core.domain.model` 反向依赖了 Compose 与 `MaterialTheme`。它既不是公告独有的
 * 需求（评论、简介里的裸链接同样要能点），也不该由数据类提供，故落到 UI 层。
 */

/**
 * 找出 [text] 里应被视为链接的区间，供 [LinkifiedText] 与单测共用。
 *
 * 与旧实现（`https?://[\w-]+(\.[\w-]+)+([/?%&=]*)?`）的差别：旧式在域名之后只吃
 * `/?%&=` 这几个字符，于是 `https://example.com/some/path` 只会把 `https://example.com/`
 * 变成链接，点开是个坏链。这里改成「吃掉所有非空白、非包裹符号的字符」，
 * 再按 [TRAILING_TRIM] 剥掉句末标点 —— 公告正文里的链接几乎总跟在标点后面。
 */
internal fun findLinkRanges(text: String): List<IntRange> =
    LINK_REGEX.findAll(text).mapNotNull { match ->
        var end = match.range.last
        while (end >= match.range.first && text[end] in TRAILING_TRIM) end--
        if (end < match.range.first) null else match.range.first..end
    }.toList()

/** 允许出现在 URL 内部的字符：排除空白与各种包裹符号（`<a href="…">` 的复制粘贴常见）。 */
private val LINK_REGEX = Regex("""https?://[^\s<>()\[\]{}"'`，。；：！？、“”‘’《》]+""")

/**
 * 句末标点与包裹符号。URL 合法字符集里本来就有 `.,;:!?'` 等，
 * 因此只能在**紧邻结尾**处剥，不能从字符集里删。
 */
private val TRAILING_TRIM = ".,;:!?'\"`)]}>,，。；：！？、“”‘’《》".toSet()

@Composable
fun LinkifiedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    linkStyle: SpanStyle = SpanStyle(
        color = linkColor,
        textDecoration = TextDecoration.Underline,
    ),
) {
    Text(
        text = buildLinkifiedString(text, linkStyle),
        modifier = modifier,
        style = style,
    )
}

/** 按 [findLinkRanges] 的结果拼装 [AnnotatedString]；无链接时等价于原文。 */
internal fun buildLinkifiedString(
    text: String,
    linkStyle: SpanStyle,
): androidx.compose.ui.text.AnnotatedString {
    val ranges = findLinkRanges(text)
    if (ranges.isEmpty()) {
        return buildAnnotatedString { append(text) }
    }
    return buildAnnotatedString {
        var cursor = 0
        ranges.forEach { range ->
            if (range.first > cursor) append(text.substring(cursor, range.first))
            val url = text.substring(range.first, range.last + 1)
            withLink(
                LinkAnnotation.Url(
                    url = url,
                    styles = TextLinkStyles(style = linkStyle),
                )
            ) {
                append(url)
            }
            cursor = range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}
