package lovehan1me.feature.search

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.search_video_hint
import lovehan1me.clear_checkin
import lovehan1me.back
import lovehan1me.advanced_search
import lovehan1me.ic_filter_list
import lovehan1me.ic_close
import lovehan1me.ic_arrow_back
import lovehan1me.ui.component.FilledIconButton
import lovehan1me.ui.component.IconButton
import lovehan1me.ui.theme.HanimeDefaults



// ─────────────────────────────────────────────
// 搜索 App Bar
//
// M3 对齐说明：单容器 `surfaceContainerHigh`（M3 SearchBar 容器色）+ 外形
// `extraLargeIncreased`，不再双层 Surface 叠 tint（旧：surface 包
// surfaceContainerHighest，两层 tonal 叠加后实际色不可预测）。
// 阴影保留 4.dp：搜索条浮于滚动结果之上（busy 背景），属 M3 允许 shadow 的例外。
// ─────────────────────────────────────────────

@Composable
fun SearchAppBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onOpenAdvancedSearch: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    showBack: Boolean = true,
    showFilterButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    val kb = LocalSoftwareKeyboardController.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLargeIncreased,
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = HanimeDefaults.Spacing.large,
                vertical = HanimeDefaults.Spacing.medium,
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = HanimeDefaults.Spacing.small,
                    vertical = HanimeDefaults.Spacing.small,
                )
        ) {
            // tab 模式（发现页）无上层可退，隐藏返回箭头（返回键会误删顶层 tab）。
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        painterResource(Res.drawable.ic_arrow_back),
                        contentDescription = stringResource(Res.string.back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .focusRequester(focusRequester)
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .onFocusChanged { onFocusChanged(it.isFocused) }
                        .padding(horizontal = HanimeDefaults.Spacing.large),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        kb?.hide()
                        onSearch()
                    }),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (query.isEmpty()) {
                                Text(
                                    text = stringResource(Res.string.search_video_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            inner()
                        }
                    })
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painterResource(Res.drawable.ic_close),
                        contentDescription = stringResource(Res.string.clear_checkin),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 宽屏常驻筛选栏时此按钮隐藏，筛选走左栏。
            if (showFilterButton) {
                FilledIconButton(onClick = onOpenAdvancedSearch) {
                    Icon(
                        painterResource(Res.drawable.ic_filter_list),
                        contentDescription = stringResource(Res.string.advanced_search)
                    )
                }
            }
        }
    }
}
