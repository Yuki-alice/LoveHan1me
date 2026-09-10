package lovehan1me.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import lovehan1me.Res
import lovehan1me.settings_tip
import lovehan1me.ic_info
import lovehan1me.ui.theme.HanimeDefaults

@Composable
fun SettingsPlainBox(
    text: String,
    modifier: Modifier = Modifier,
) {
    val tipText = stringResource(Res.string.settings_tip)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = HanimeDefaults.Spacing.itemHorizontal / 2),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_info),
            contentDescription = null,
        )
        Spacer(Modifier.size(HanimeDefaults.Spacing.itemVertical / 2))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = "$tipText $text"
            },
        )
    }
}
