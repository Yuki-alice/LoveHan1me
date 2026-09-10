package me.lovehan1me.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import me.lovehan1me.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun TripleButtonDialog(
    visible: Boolean,
    title: String,
    message: String? = null,
    negativeText: String,
    neutralText: String,
    positiveText: String,
    onNegative: () -> Unit,
    onNeutral: () -> Unit,
    onPositive: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = message?.let { { Text(it) } },
        confirmButton = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onNegative) { DialogButtonText(negativeText) }
                TextButton(onClick = onNeutral) { DialogButtonText(neutralText) }
                TextButton(onClick = onPositive) { DialogButtonText(positiveText) }
            }
        },
    )
}

@Composable
private fun DialogButtonText(text: String) {
    Text(
        text = text,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

