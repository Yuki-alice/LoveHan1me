package lovehan1me.core.platform

import androidx.compose.runtime.Composable

/**
 * P6d-4E：备份文件选择器（原 :app ActivityResultContracts.CreateDocument/OpenDocument ×6）。
 * Android 包装 ActivityResult；桌面 JFileChooser；iOS UIDocumentPicker
 * （见 `IosBackupFiles.kt`，导出为"写临时再导出"两步）。
 */
expect @Composable fun rememberBackupExportLauncher(onResult: (String?) -> Unit): (String) -> Unit

expect @Composable fun rememberBackupImportLauncher(onResult: (String?) -> Unit): () -> Unit
