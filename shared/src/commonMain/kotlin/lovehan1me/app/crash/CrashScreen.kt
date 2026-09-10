package lovehan1me.app.crash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lovehan1me.Res
import lovehan1me.crash_unexpected_title
import lovehan1me.crash_unexpected_message
import lovehan1me.crash_restart_app_summary
import lovehan1me.crash_restart_app
import lovehan1me.crash_page_title
import lovehan1me.crash_exit_app_summary
import lovehan1me.crash_exit_app
import lovehan1me.crash_copy_log_summary
import lovehan1me.crash_copy_log
import lovehan1me.crash_log_title
import lovehan1me.crash_actions
import lovehan1me.ic_bug_report
import lovehan1me.ic_error_outline
import lovehan1me.ic_exit_to_app
import lovehan1me.ic_refresh
import lovehan1me.ui.component.SettingNavigationItem
import lovehan1me.ui.component.SettingsSectionTitle
import lovehan1me.ui.component.SettingsSegmentedGroup
import lovehan1me.ui.component.appbar.HanimeScaffold
import lovehan1me.ui.theme.HanimeDefaults

@Composable
fun CrashScreen(
    crashReport: String,
    packageName: String,
    onCopyLog: () -> Unit,
    onRestartApp: () -> Unit,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val highlightedReport = remember(
        crashReport,
        packageName,
        primaryContainer,
        onPrimaryContainer,
    ) {
        val lines = crashReport.lines()
        buildAnnotatedString {
            lines.forEachIndexed { index, line ->
                if (packageName.isNotBlank() && line.contains(packageName)) {
                    withStyle(
                        SpanStyle(
                            color = onPrimaryContainer,
                            background = primaryContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    ) {
                        append(line)
                    }
                } else {
                    append(line)
                }
                if (index < lines.lastIndex) append('\n')
            }
        }
    }

    HanimeScaffold(
        title = stringResource(Res.string.crash_page_title),
        onBack = null,
        modifier = modifier.fillMaxSize(),
        contentHorizontalPadding = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = HanimeDefaults.Spacing.contentHorizontal,
                        vertical = HanimeDefaults.Spacing.small,
                    ),
            ) {
                Surface(
                    color = lerp(
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.surface,
                        0.6f
                    ),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_error_outline),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(Res.string.crash_unexpected_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(Res.string.crash_unexpected_message),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                SettingsSectionTitle(titleRes = Res.string.crash_actions)
                SettingsSegmentedGroup {
                    SettingNavigationItem(
                        title = stringResource(Res.string.crash_copy_log),
                        summary = stringResource(Res.string.crash_copy_log_summary),
                        iconRes = Res.drawable.ic_bug_report,
                        onClick = onCopyLog,
                    )
                    SettingNavigationItem(
                        title = stringResource(Res.string.crash_restart_app),
                        summary = stringResource(Res.string.crash_restart_app_summary),
                        iconRes = Res.drawable.ic_refresh,
                        onClick = onRestartApp,
                    )
                    SettingNavigationItem(
                        title = stringResource(Res.string.crash_exit_app),
                        summary = stringResource(Res.string.crash_exit_app_summary),
                        iconRes = Res.drawable.ic_exit_to_app,
                        onClick = onExitApp,
                    )
                }

                SettingsSectionTitle(titleRes = Res.string.crash_log_title)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    SelectionContainer {
                        Text(
                            text = highlightedReport,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp,
                            ),
                        )
                    }
                }
                Spacer(modifier = Modifier.size(24.dp))
            }
        }
    }
}
