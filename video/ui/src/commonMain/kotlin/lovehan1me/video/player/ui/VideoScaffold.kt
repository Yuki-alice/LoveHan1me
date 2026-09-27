/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import lovehan1me.video.player.ui.support.AniAnimatedVisibility
import lovehan1me.video.player.ui.support.enterFade
import lovehan1me.video.player.ui.support.exitFade
import lovehan1me.video.player.ui.support.keepLayoutWhenHidden
import lovehan1me.video.player.ui.support.slightlyWeaken

/**
 * 播放器框架：只负责层与层之间的位置和显隐动画，不决定任何一层画什么。
 *
 * 自下而上的层级：
 *
 * - 画面 [video]
 * - 弹幕 [danmakuHost]
 * - 手势 [gestureHost]
 * - 调试信息 [playerStatsOverlay]
 * - 控件 [topBar]、[rhsButtons]、[gestureLock]、[bottomBar]、[detachedProgressSlider]、[floatingBottomEnd]
 * - 悬浮消息 [floatingMessage]
 *
 * @param expanded 是否全屏。全屏时框架 fillMaxHeight，否则被限制在一个 16:9 框里。
 * @param topBar [lovehan1me.video.player.ui.top.PlayerTopBar]
 * @param bottomBar [lovehan1me.video.player.ui.progress.PlayerControllerBar]
 * @param gestureHost 手势区域（快进快退、音量等）。See PlayerGestureHost
 * @param floatingMessage 居中的悬浮消息，例如正在缓冲
 * @param framePreviewOverlay 落在画面正中央的叠层，不应用系统窗口边距
 * @param rhsButtons 右侧控制栏（锁定手势等）
 */
@Composable
fun VideoScaffold(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = WindowInsets.safeContent,
    maintainAspectRatio: Boolean = !expanded,
    controllerState: PlayerControllerState,
    gestureLocked: Boolean = false,
    topBar: @Composable RowScope.() -> Unit = {},
    video: @Composable BoxScope.() -> Unit = {},
    danmakuHost: @Composable BoxScope.() -> Unit = {},
    gestureHost: @Composable BoxWithConstraintsScope.() -> Unit = {},
    floatingMessage: @Composable BoxScope.() -> Unit = {},
    rhsButtons: @Composable ColumnScope.() -> Unit = {},
    gestureLock: @Composable ColumnScope.() -> Unit = {},
    bottomBar: @Composable RowScope.() -> Unit = {},
    detachedProgressSlider: @Composable () -> Unit = {},
    floatingBottomEnd: @Composable RowScope.() -> Unit = {},
    rhsSheet: @Composable () -> Unit = {},
    centerOverlay: @Composable BoxScope.() -> Unit = {},
    framePreviewOverlay: @Composable BoxScope.() -> Unit = {},
    playerStatsOverlay: @Composable BoxScope.() -> Unit = {},
) {
    val inlineSliderOnly = controllerState.visibility == ControllerVisibility.InlineSliderOnly
    val controllerVisibility = controllerState.visibility
        .withGestureLocked(gestureLocked)
        .withExpanded(expanded)

    val enterTransition = enterFade()
    val exitTransition = exitFade()
    BoxWithConstraints(
        modifier.then(if (expanded) Modifier.fillMaxHeight() else Modifier.fillMaxWidth()),
        contentAlignment = Alignment.Center,
    ) { // 16:9 box
        Box(
            Modifier
                .then(
                    if (!maintainAspectRatio) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.fillMaxWidth().height(maxWidth * 9 / 16) // 16:9 box
                    },
                ),
        ) {
            Box(
                Modifier
                    .background(Color.Transparent)
                    .matchParentSize(), // no window insets for video
            ) {
                video()
                Box(Modifier.matchParentSize()) // 点击事件不许传到 video 里
            }

            // 弹幕铺满播放器这块矩形（含渲染面自己留的黑边）：弹幕区是"播放器区"，
            // 不是"画面区"。上下各让 8dp，全屏时再让开状态栏与导航条 ——
            // 否则第一行字压在状态栏图标上、最后一行压在手势条上。
            Box(
                Modifier
                    .matchParentSize()
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.Vertical)),
            ) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                    danmakuHost()
                }
            }

            // 控制手势
            BoxWithConstraints(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                gestureHost()
            }

            Box(
                Modifier.matchParentSize()
                    .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                    .padding(12.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                playerStatsOverlay()
            }

            Box(Modifier) {
                Column(Modifier.fillMaxSize().background(Color.Transparent)) {
                    // 顶部控制栏: 返回键, 标题, 设置
                    AniAnimatedVisibility(
                        visible = controllerVisibility.topBar || inlineSliderOnly,
                        enter = enterTransition,
                        exit = exitTransition,
                    ) {
                        Box {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color.Transparent.copy(0.72f),
                                            0.32f to Color.Transparent.copy(0.45f),
                                            1f to Color.Transparent,
                                        ),
                                    ),
                            )
                            val alwaysOnRequester = rememberAlwaysOnRequester(controllerState, "topBar")

                            Column(
                                Modifier
                                    .keepLayoutWhenHidden(inlineSliderOnly)
                                    .hoverToRequestAlwaysOn(alwaysOnRequester)
                                    .fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                                        topBar()
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                            }

                            Box(
                                Modifier.matchParentSize()
                                    .keepLayoutWhenHidden(inlineSliderOnly)
                                    .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.Top))
                                    .padding(top = 8.dp),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                centerOverlay()
                            }
                        }
                    }

                    Box(Modifier.weight(1f, fill = true).fillMaxWidth())

                    Column {
                        // 底部控制栏: 播放/暂停, 进度条, 切换全屏
                        AniAnimatedVisibility(
                            visible = controllerVisibility.bottomBar,
                            enter = enterTransition,
                            exit = exitTransition,
                        ) {
                            val alwaysOnRequester = rememberAlwaysOnRequester(controllerState, "bottomBar")
                            Column(
                                Modifier
                                    .hoverToRequestAlwaysOn(alwaysOnRequester)
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            val event = awaitPointerEvent()
                                            if (event.changes.all { it.pressed }) {
                                                // 点住底栏里的按钮时不许自动隐藏
                                                alwaysOnRequester.request()
                                            }
                                            var releaseEvent = awaitPointerEvent()
                                            while (releaseEvent.changes.any { it.pressed }) {
                                                releaseEvent = awaitPointerEvent()
                                            }
                                            alwaysOnRequester.cancelRequest()
                                        }
                                    }
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            1 - 0.32f to Color.Transparent.copy(0.45f),
                                            1f to Color.Transparent.copy(0.72f),
                                        ),
                                    ),
                            ) {
                                Spacer(Modifier.height(if (expanded) 12.dp else 6.dp))
                                Row(
                                    Modifier.fillMaxWidth()
                                        .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CompositionLocalProvider(LocalContentColor provides Color.White) {
                                        bottomBar()
                                    }
                                }
                            }
                        }
                        AniAnimatedVisibility(
                            visible = controllerVisibility.detachedSlider,
                            enter = enterTransition,
                            exit = exitTransition,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                                    .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                            ) {
                                detachedProgressSlider()
                            }
                        }
                    }
                }
                AniAnimatedVisibility(
                    controllerVisibility.floatingBottomEnd && !expanded,
                    Modifier.align(Alignment.BottomEnd),
                    enter = enterTransition,
                    exit = exitTransition,
                ) {
                    Row(
                        Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.End)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        CompositionLocalProvider(LocalContentColor provides Color.White) {
                            floatingBottomEnd()
                        }
                    }
                }
            }
            Column(
                Modifier.fillMaxSize().background(Color.Transparent)
                    .windowInsetsPadding(contentWindowInsets.only(WindowInsetsSides.End)),
            ) {
                Box(Modifier.weight(1f, fill = true).fillMaxWidth()) {
                    Column(
                        Modifier.padding(end = 16.dp).align(Alignment.CenterEnd),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AniAnimatedVisibility(
                            visible = controllerVisibility.rhsBar,
                            enter = enterTransition,
                            exit = exitTransition,
                        ) {
                            rhsButtons()
                        }

                        // 与控制器分开, 保证控制器显隐时它的定位不动
                        AniAnimatedVisibility(
                            visible = controllerVisibility.gestureLock,
                            enter = enterTransition,
                            exit = exitTransition,
                        ) {
                            gestureLock()
                        }
                    }
                }
            }

            // 悬浮消息, 例如正在缓冲
            Box(
                Modifier.matchParentSize().windowInsetsPadding(contentWindowInsets),
                contentAlignment = Alignment.Center,
            ) {
                ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground.slightlyWeaken()) {
                        floatingMessage()
                    }
                }
            }
            // 紧凑布局下的 FramePreview 弹层
            Box(
                Modifier.matchParentSize(),
                contentAlignment = Alignment.Center,
            ) {
                framePreviewOverlay()
            }
            // 右侧 sheet
            Box(Modifier.matchParentSize().windowInsetsPadding(contentWindowInsets)) {
                rhsSheet()
            }
        }
    }
}

@Stable
private fun ControllerVisibility.withGestureLocked(gestureLocked: Boolean): ControllerVisibility {
    return if (gestureLocked) {
        copy(
            topBar = false,
            bottomBar = false,
            detachedSlider = false,
            rhsBar = false,
        )
    } else {
        this
    }
}

@Stable
private fun ControllerVisibility.withExpanded(isExpanded: Boolean): ControllerVisibility {
    return if (isExpanded) {
        copy(floatingBottomEnd = false)
    } else {
        this
    }
}
