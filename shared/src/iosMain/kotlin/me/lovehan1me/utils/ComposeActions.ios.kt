package me.lovehan1me.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import me.lovehan1me.Res
import me.lovehan1me.copy_to_clipboard
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

// P6d-4：iOS 剪贴板暂降级 no-op（复制无效果，UI 有 toast 反馈）；真实现随 P7 平台能力收口
actual fun createTextClipEntry(text: String): ClipEntry? = null

// M3：iOS 分享面板（UIActivityViewController）随 M-后续接入，当前降级为复制链接 + toast。
@Composable
actual fun rememberShareText(): (String, String?) -> Unit {
    val copyTextToClipboard = rememberCopyTextToClipboard()
    val scope = rememberCoroutineScope()
    return { content, _ ->
        copyTextToClipboard(content)
        scope.launch { SonnerToast.success(getString(Res.string.copy_to_clipboard)) }
    }
}
