package lovehan1me.core.platform

import androidx.compose.runtime.Composable

// 阶段一④：iOS 文件选择真实现（UIDocumentPicker，见 IosBackupFiles.kt）。
//
// 导出是“写临时再导出”两步：此 launcher 只回显建议文件名当 uri，
// 真正的临时落盘 + 系统导出窗发生在写时（全量备份 `openBackupSink.close()` /
// lists `writeBackupText`），共享层流程零改动。
actual @Composable
fun rememberBackupExportLauncher(onResult: (String?) -> Unit): (String) -> Unit =
    { suggestedName -> onResult(suggestedName) }

actual @Composable
fun rememberBackupImportLauncher(onResult: (String?) -> Unit): () -> Unit =
    { presentBackupImportPicker(onResult) }
