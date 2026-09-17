package lovehan1me.feature.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lovehan1me.Res
import lovehan1me.ic_arrow_back
import lovehan1me.ic_heart
import lovehan1me.ic_heart_outline
import lovehan1me.ic_more_vert
import lovehan1me.gif_capture
import lovehan1me.ic_panel_close
import lovehan1me.ic_panel_open
import lovehan1me.screenshot
import lovehan1me.ui.component.IconButton
import lovehan1me.ui.theme.HanimeDefaults
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 播放器顶栏 —— 照抄 Kazumi（`player_item_panel.dart` → `_buildTopControls`）。
 *
 * `[返回][标题 weight 1f][收藏][更多][折叠]`（48dp 按钮紧贴，无间距）。
 * 标题单行省略（数据源没有剧集标题，保持单行是定过的）；白字 + 深灰描边。
 * 更多菜单里放录制 GIF 与截图（Kazumi 把次级功能收进 more，我们没有投屏/定时，
 * 只放这两个真实可用的）；面板折叠固定最右（要求里唯一与 Kazumi 不同的点，
 * Kazumi 的选集面板键在底栏）。
 *
 * 标准 M3 组件，布局尺寸即绘制尺寸。
 */
@Composable
internal fun BoxScope.PlayerTopBar(
    visible: Boolean,
    isFullscreen: Boolean,
    title: String,
    deviceTime: String,
    frameCaptureEnabled: Boolean,
    onCaptureScreenshot: (() -> Unit)?,
    onOpenGifCapture: (() -> Unit)?,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit = {},
    isFav: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    /**
     * animeko 右栏折叠开关：宽屏右栏存在时为 true，在动作区末尾放折叠按钮。
     */
    showSidebarToggle: Boolean = false,
    sidebarVisible: Boolean = true,
    onToggleSidebar: (Boolean) -> Unit = {},
    /**
     * animeko 的「expanded」形态：宽屏双栏 / 全屏时为 true。
     * 只决定标题显隐（窄屏标题在下方简介区，不在顶栏重复）。
     */
    expanded: Boolean = true,
    /** 有弹窗打开时通知调用方（控件自动隐藏要给弹窗让路）。 */
    onMenuOpenChange: (Boolean) -> Unit = {},
    /**
     * 悬停交互源：**顶栏容器**上的 hover 用来"请求控件常亮"。
     */
    hoverInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.TopCenter),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                // 悬停顶栏 = 请求控件常亮（与底栏同一语义）
                .hoverable(hoverInteractionSource)
        ) {
            // 顶 scrim：matchParentSize 压在内容区上。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                HanimeDefaults.Overlay.scrimTopEnd,
                                HanimeDefaults.Overlay.scrimTopStart,
                            )
                        )
                    )
            )
            TopAppBar(
                title = {
                    if (expanded) {
                        TopBarTitleText(text = title)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_arrow_back),
                            contentDescription = null,
                            modifier = Modifier.size(HanimeDefaults.PlayerSizes.iconLarge)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            painter = painterResource(if (isFav) Res.drawable.ic_heart
                                else Res.drawable.ic_heart_outline),
                            contentDescription = null,
                            tint = if (isFav) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                HanimeDefaults.Overlay.onScrim
                            },
                            modifier = Modifier.size(HanimeDefaults.PlayerSizes.iconLarge)
                        )
                    }

                    if (frameCaptureEnabled &&
                        (onOpenGifCapture != null || onCaptureScreenshot != null)
                    ) {
                        KazumiIconMenu(
                            icon = {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_more_vert),
                                    contentDescription = null,
                                    modifier = Modifier.size(HanimeDefaults.PlayerSizes.iconLarge)
                                )
                            },
                            onOpenChange = onMenuOpenChange,
                        ) { close ->
                            if (onOpenGifCapture != null) {
                                KazumiMoreOption(
                                    label = stringResource(Res.string.gif_capture),
                                    onClick = {
                                        close()
                                        onOpenGifCapture()
                                    },
                                )
                            }
                            if (onCaptureScreenshot != null) {
                                KazumiMoreOption(
                                    label = stringResource(Res.string.screenshot),
                                    onClick = {
                                        close()
                                        onCaptureScreenshot()
                                    },
                                )
                            }
                        }
                    }

                    if (isFullscreen) {
                        Spacer(modifier = Modifier.width(10.dp))

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = deviceTime,
                                color = HanimeDefaults.Overlay.textTertiary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            PlayerBatteryIndicator()
                        }
                    }

                    // 面板折叠固定最右（与 Kazumi 唯一不同的点：Kazumi 的选集键在底栏）。
                    if (showSidebarToggle) {
                        IconButton(onClick = { onToggleSidebar(!sidebarVisible) }) {
                            Icon(
                                painter = painterResource(
                                    if (sidebarVisible) Res.drawable.ic_panel_close
                                    else Res.drawable.ic_panel_open
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(HanimeDefaults.PlayerSizes.iconLarge)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = HanimeDefaults.Overlay.onScrim,
                    titleContentColor = HanimeDefaults.Overlay.textPrimary,
                    actionIconContentColor = HanimeDefaults.Overlay.onScrim,
                ),
                // 壳层已处理状态栏边距；传默认 insets 会在竖屏重复垫一次。
                windowInsets = WindowInsets(0),
            )
        }
    }
}

/**
 * 更多菜单的选项行（Kazumi `_menuLabel`：高 48dp、min 112dp、左对齐）。
 */
@Composable
private fun KazumiMoreOption(
    label: String,
    onClick: () -> Unit,
) {
    val haptic = lovehan1me.ui.component.rememberHapticFeedback()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptic()
                    onClick()
                },
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
        )
    }
}

/**
 * 顶栏单行标题（白字 + 深灰描边）。
 */
@Composable
private fun TopBarTitleText(text: String) {
    val density = LocalDensity.current
    val baseStyle = MaterialTheme.typography.titleLarge
    val strokeWidthPx = with(density) {
        val size = baseStyle.fontSize
        (if (size.isSp) size else 22.sp).toPx() / 15f
    }
    Box {
        Text(
            text = text,
            color = HanimeDefaults.Overlay.backdrop,
            style = baseStyle.copy(
                drawStyle = Stroke(
                    width = strokeWidthPx,
                    join = StrokeJoin.Round,
                )
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = text,
            color = HanimeDefaults.Overlay.textPrimary,
            style = baseStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
