package me.lovehan1me.ui.screen.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import org.jetbrains.compose.resources.stringResource
import me.lovehan1me.Res
import me.lovehan1me.loading
import me.lovehan1me.logic.state.VideoLoadingState
import me.lovehan1me.ui.component.content.EmptyContent
import me.lovehan1me.ui.component.content.ErrorContent
import me.lovehan1me.ui.component.content.LoadingContent

@Composable
fun VideoScreen(
    state: VideoLoadingState<*>,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        content()

        if (LocalInspectionMode.current) {
            when (state) {
                is VideoLoadingState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingContent(message = stringResource(Res.string.loading))
                    }
                }

                is VideoLoadingState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorContent(
                            title = "视频加载失败",
                            message = state.throwable.message,
                            onRetry = onRetry,
                        )
                    }
                }

                is VideoLoadingState.NoContent -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyContent(hint = "该影片可能不存在")
                    }
                }

                else -> Unit
            }
        }
    }
}
