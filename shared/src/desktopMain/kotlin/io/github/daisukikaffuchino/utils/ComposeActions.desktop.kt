@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.daisukikaffuchino.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.copy_to_clipboard
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import java.awt.datatransfer.StringSelection

actual fun createTextClipEntry(text: String): ClipEntry? = ClipEntry(StringSelection(text))

// M3：桌面无系统分享面板，降级为复制链接 + toast（调用方签名不变）。
@Composable
actual fun rememberShareText(): (String, String?) -> Unit {
    val copyTextToClipboard = rememberCopyTextToClipboard()
    val scope = rememberCoroutineScope()
    return { content, _ ->
        copyTextToClipboard(content)
        scope.launch { SonnerToast.success(getString(Res.string.copy_to_clipboard)) }
    }
}
