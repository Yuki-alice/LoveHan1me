package me.lovehan1me.utils

import androidx.compose.ui.text.AnnotatedString

actual fun parseHtmlToAnnotatedString(html: String): AnnotatedString =
    AnnotatedString(Regex("<[^>]+>").replace(html, ""))
