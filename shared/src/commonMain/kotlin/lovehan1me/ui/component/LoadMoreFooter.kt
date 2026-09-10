package lovehan1me.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.load_complete
import lovehan1me.load_complete_with_pages
import lovehan1me.load_failed_retry
import lovehan1me.desktop.loading
import lovehan1me.core.domain.state.PageLoadingState
import org.jetbrains.compose.resources.stringResource

/**
 * 加载更多底部组件
 * @param state 加载状态
 * @param modifier 修饰符
 * @param textColor 文字颜色
 * @param loadedPage 已加载页数
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadMoreFooter(
    state: PageLoadingState<*>,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    loadedPage: Int? = null,
    isLoadingMore: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            state is PageLoadingState.Loading || isLoadingMore -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(Res.string.loading),
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            state is PageLoadingState.NoMoreData -> {
                Text(
                    text = if (loadedPage == null)
                        stringResource(Res.string.load_complete)
                    else
                        stringResource(Res.string.load_complete_with_pages, loadedPage),
                    color = textColor.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            state is PageLoadingState.Success<*> -> {}

            state is PageLoadingState.Error -> {
                Text(
                    text = stringResource(Res.string.load_failed_retry),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
