package lovehan1me.feature.home.homepage.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import lovehan1me.ui.component.HapticButton as Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import lovehan1me.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.update_now
import lovehan1me.update_available_title
import lovehan1me.ignore_this_update
import lovehan1me.force_update_notice
import lovehan1me.ic_download
import lovehan1me.ic_security_update
import lovehan1me.ic_warning
import lovehan1me.core.domain.model.AppUpdateInfo

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppUpdateCard(
    updateInfo: AppUpdateInfo,
    onUpdateClick: () -> Unit,
    onIgnoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_security_update),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(Res.string.update_available_title, updateInfo.versionName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }

            if (updateInfo.forceUpdate) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_warning),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(Res.string.force_update_notice),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (updateInfo.updateDescription.isNotBlank()) {
                Text(
                    text = updateInfo.updateDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!updateInfo.forceUpdate) {
                    TextButton(onClick = onIgnoreClick) {
                        Text(stringResource(Res.string.ignore_this_update))
                    }
                }
                Button(onClick = onUpdateClick) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_download),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.update_now))
                }
            }
        }
    }
}
private fun previewUpdateInfo(forceUpdate: Boolean) = AppUpdateInfo(
    versionName = "26.1.0",
    versionCode = 260720,
    downloadUrl = "https://github.com/daisukiKaffuChino/Han1meViewer/releases/latest",
    updateDescription = "Includes stability improvements and interface refinements.",
    forceUpdate = forceUpdate,
)
