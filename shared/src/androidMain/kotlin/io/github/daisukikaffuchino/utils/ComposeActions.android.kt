package io.github.daisukikaffuchino.utils

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

actual fun createTextClipEntry(text: String): ClipEntry? = ClipEntry(ClipData.newPlainText(null, text))
