package lovehan1me.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch

/**
 * P6d-4：自上游 utils 库 ComposeActions.kt 内联（原实现 Android-only）。
 * 跨平台创建文本 ClipEntry 的平台差异由 [createTextClipEntry] 承担。
 */
expect fun createTextClipEntry(text: String): ClipEntry?

@Composable
fun rememberCopyTextToClipboard(): (CharSequence) -> Unit {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return { text ->
        val entry = createTextClipEntry(text.toString())
        if (entry != null) {
            scope.launch { clipboard.setClipEntry(entry) }
        }
    }
}

/**
 * M3：系统分享面板（原 `:app` `rememberShareText`，Intent chooser）。
 * 不支持的平台以降级实现提供（见各端 actual）。
 */
@Composable
expect fun rememberShareText(): (String, String?) -> Unit
