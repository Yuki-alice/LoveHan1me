package io.github.daisukikaffuchino.han1meviewer.logic.platform

import androidx.compose.runtime.Composable

// P6d-4E：iOS 文件选择降级 no-op（沙盒 UIDocumentPicker 随 P7）
actual @Composable
fun rememberBackupExportLauncher(onResult: (String?) -> Unit): (String) -> Unit = { }

actual @Composable
fun rememberBackupImportLauncher(onResult: (String?) -> Unit): () -> Unit = { }
