package io.github.daisukikaffuchino.han1meviewer.ui.screen.home.myplaylist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import io.github.daisukikaffuchino.han1meviewer.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.daisukikaffuchino.han1meviewer.R

@Composable
fun PlaylistEditDialog(
    title: String,
    initialTitle: String = "",
    initialDescription: String = "",
    onConfirm: (title: String, description: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var playlistTitle by remember(initialTitle) { mutableStateOf(initialTitle) }
    var playlistDescription by remember(initialDescription) { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = playlistTitle,
                    onValueChange = { playlistTitle = it },
                    label = { Text(stringResource(R.string.playlist_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                OutlinedTextField(
                    value = playlistDescription,
                    onValueChange = { playlistDescription = it },
                    label = { Text(stringResource(R.string.playlist_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(playlistTitle, playlistDescription)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
