package lovehan1me.core.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

actual @Composable
fun rememberBackupExportLauncher(onResult: (String?) -> Unit): (String) -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> onResult(uri?.toString()) }
    return { suggestedName -> launcher.launch(suggestedName) }
}

actual @Composable
fun rememberBackupImportLauncher(onResult: (String?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> onResult(uri?.toString()) }
    return { launcher.launch(arrayOf("application/json", "text/*", "*/*")) }
}
