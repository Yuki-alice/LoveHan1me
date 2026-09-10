package lovehan1me.core.platform

import androidx.compose.runtime.Composable
import java.io.File
import javax.swing.JFileChooser

// P6d-4E：桌面用 JFileChooser（非 EDT 显示有可用性风险，P7 换 Compose 生态文件选择器打磨）
actual @Composable
fun rememberBackupExportLauncher(onResult: (String?) -> Unit): (String) -> Unit {
    return { suggestedName ->
        Thread {
            val chooser = JFileChooser()
            chooser.selectedFile = File(suggestedName)
            val result = chooser.showSaveDialog(null)
            onResult(if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null)
        }.apply { isDaemon = true }.start()
    }
}

actual @Composable
fun rememberBackupImportLauncher(onResult: (String?) -> Unit): () -> Unit {
    return {
        Thread {
            val chooser = JFileChooser()
            val result = chooser.showOpenDialog(null)
            onResult(if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null)
        }.apply { isDaemon = true }.start()
    }
}
