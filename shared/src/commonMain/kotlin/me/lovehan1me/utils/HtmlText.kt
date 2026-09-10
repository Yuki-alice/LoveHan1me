package me.lovehan1me.utils

import androidx.compose.ui.text.AnnotatedString

/**
 * P6d-4E：HTML → AnnotatedString（开源许可全文展示用）。
 * CMP 1.12 的 ui-text 无跨平台 fromHtml（实测 ui-desktop jar 0 命中），平台分治：
 * Android 用 androidx 实现；桌面/iOS 降级为去标签纯文本（许可正文主体可读）。
 */
expect fun parseHtmlToAnnotatedString(html: String): AnnotatedString
