package io.github.daisukikaffuchino.han1meviewer.logic.platform

import androidx.compose.runtime.Composable

/**
 * P6d-4E：备份文件选择器（原 :app ActivityResultContracts.CreateDocument/OpenDocument ×6）。
 * Android 包装 ActivityResult；桌面 JFileChooser；iOS no-op（P7 沙盒方案）。
 */
expect @Composable fun rememberBackupExportLauncher(onResult: (String?) -> Unit): (String) -> Unit

expect @Composable fun rememberBackupImportLauncher(onResult: (String?) -> Unit): () -> Unit
