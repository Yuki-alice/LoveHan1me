package lovehan1me.core.util

import androidx.compose.ui.text.AnnotatedString

actual fun parseHtmlToAnnotatedString(html: String): AnnotatedString =
    AnnotatedString(Regex("<[^>]+>").replace(html, ""))
