package lovehan1me.feature.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import lovehan1me.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import lovehan1me.Res
import lovehan1me.user_terms
import lovehan1me.usage_notice_content
import lovehan1me.confirm

@Composable
fun UsageTermsDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.user_terms)) },
        text = {
            SelectionContainer {
                Text(
                    text = stringResource(Res.string.usage_notice_content),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.confirm))
            }
        },
    )
}
