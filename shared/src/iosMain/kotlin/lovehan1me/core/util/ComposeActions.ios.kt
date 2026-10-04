package lovehan1me.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import kotlinx.coroutines.launch
import lovehan1me.Res
import lovehan1me.copy_to_clipboard
import org.jetbrains.compose.resources.getString

// C3a：iOS 剪贴板真实现（common 工厂，底层走 UIPasteboard）。
@OptIn(ExperimentalComposeUiApi::class)
actual fun createTextClipEntry(text: String): ClipEntry? = ClipEntry.withPlainText(text)

// C3b：iOS 文本走系统分享面板（title 无处可放，签名保留兼容）。
// iPad 无安全呈现路径（见 presentActivitySheet）：退到复制 + toast。
@Composable
actual fun rememberShareText(): (String, String?) -> Unit {
    val copyTextToClipboard = rememberCopyTextToClipboard()
    val scope = rememberCoroutineScope()
    return { content, _ ->
        if (isPad()) {
            copyTextToClipboard(content)
            scope.launch { AppToast.success(getString(Res.string.copy_to_clipboard)) }
        } else {
            scope.launch { presentActivitySheet(listOf(content)) }
        }
    }
}
