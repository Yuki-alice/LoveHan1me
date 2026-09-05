@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.daisukikaffuchino.utils

import androidx.compose.ui.platform.ClipEntry
import java.awt.datatransfer.StringSelection

actual fun createTextClipEntry(text: String): ClipEntry? = ClipEntry(StringSelection(text))
