package me.lovehan1me.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import me.lovehan1me.ui.component.lazy.AnimatedLazyListScope
import me.lovehan1me.ui.theme.HanimeDefaults
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun AnimatedLazyListScope.segmentedGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    item {
        SettingsSegmentedGroup(modifier = modifier, content = content)
    }
    item { Spacer(Modifier.size(HanimeDefaults.Spacing.small)) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsSegmentedGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(
            HanimeDefaults.Spacing.extraSmall,
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.largeIncreased)
            .animateContentSize(),
        content = content,
    )
}

@Composable
fun SettingsAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        content()
    }
}

// P6d-1：原 @StringRes titleRes: Int? 改为 CMP StringResource。
// 原因：调用点处在 lazy scope 非 @Composable 上下文，stringResource 只能在 shared 内 item{} 里解析。
// 调用方改传 titleRes = Res.string.x（15 个标题已随迁 shared）。
fun AnimatedLazyListScope.segmentedSection(
    titleRes: StringResource? = null,
    content: AnimatedLazyListScope.() -> Unit,
) {
    if (titleRes != null) {
        item { SettingsSectionTitle(titleRes = titleRes) }
    }
    content()
    item { Spacer(Modifier.size(HanimeDefaults.Spacing.small)) }
}

@Composable
fun SettingsSectionTitle(
    titleRes: StringResource? = null,
) {
    Text(
        text = titleRes?.let { stringResource(it) }.orEmpty(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(
                top = 12.dp,
                bottom = 8.dp,
            )
            .padding(horizontal = HanimeDefaults.Spacing.contentVertical),
    )
}
