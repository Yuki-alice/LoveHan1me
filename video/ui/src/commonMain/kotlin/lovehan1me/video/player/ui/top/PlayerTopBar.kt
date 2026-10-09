/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.top

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import lovehan1me.video.player.ui.support.LocalPlatform
import lovehan1me.video.player.ui.support.ifThen
import lovehan1me.video.player.ui.support.isDesktop
import lovehan1me.video.ui.PlayerTooltipBox
import lovehan1me.video.ui.Res
import lovehan1me.video.ui.player_back
import org.jetbrains.compose.resources.stringResource

/**
 * 播放器顶部导航栏。
 */
@Composable
fun PlayerTopBar(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit) = {},
    color: Color = MaterialTheme.colorScheme.onBackground,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    TopAppBar(
        title = {
            CompositionLocalProvider(LocalContentColor provides color) {
                if (title != null) {
                    title()
                }
            }
        },
        modifier
            .fillMaxWidth(),
        navigationIcon = {
            val back by rememberUpdatedState(onBackClick)
            val backLabel = stringResource(Res.string.player_back)
            CompositionLocalProvider(LocalContentColor provides color) {
                val focusManager by rememberUpdatedState(LocalFocusManager.current)
                PlayerTooltipBox(text = backLabel) {
                    IconButton(
                        onClick = { back() },
                        Modifier.ifThen(needWorkaroundForFocusManager) {
                            onFocusEvent {
                                if (it.hasFocus) {
                                    focusManager.clearFocus()
                                }
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = backLabel)
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        ),
        actions = {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                actions()
            }
        },
        windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
    )
}

/**
 * 桌面上焦点会滞留在控件上，让后续的全屏切换失效；进入控件时主动清焦点。
 */
val needWorkaroundForFocusManager: Boolean
    @Composable
    get() = LocalPlatform.current.isDesktop()
