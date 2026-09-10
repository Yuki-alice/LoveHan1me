package lovehan1me.core.util

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ClipEntry

actual fun createTextClipEntry(text: String): ClipEntry? = ClipEntry(ClipData.newPlainText(null, text))

// M3：原 `:app` rememberShareText 原样归位（`:app` 侧删除，调用方改 String 签名）。
@Composable
actual fun rememberShareText(): (String, String?) -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}
    return { content, title ->
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content)
        }
        launcher.launch(Intent.createChooser(shareIntent, title))
    }
}
