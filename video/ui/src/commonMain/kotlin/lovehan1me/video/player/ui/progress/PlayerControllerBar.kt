/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SubtitlesOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import lovehan1me.video.player.ui.PlaybackSpeedControllerState
import lovehan1me.video.player.ui.PlayerControllerState
import lovehan1me.video.player.ui.PlayerFullscreenState
import lovehan1me.video.player.ui.VideoAspectRatioControllerState
import lovehan1me.video.player.ui.renderAspectRatioMode
import lovehan1me.video.player.ui.support.PlatformPopupProperties
import lovehan1me.video.player.ui.support.SteppedSlider
import lovehan1me.video.player.ui.support.formatSpeedValue
import lovehan1me.video.player.ui.support.ifThen
import lovehan1me.video.player.ui.support.keepLayoutWhenHidden
import lovehan1me.video.player.ui.toggle
import lovehan1me.video.player.ui.top.needWorkaroundForFocusManager
import lovehan1me.video.ui.Res
import lovehan1me.video.ui.cancel
import lovehan1me.video.ui.player_disable_danmaku
import lovehan1me.video.ui.player_enable_danmaku
import lovehan1me.video.ui.player_gesture_volume
import lovehan1me.video.ui.player_mute
import lovehan1me.video.ui.player_next_episode
import lovehan1me.video.ui.player_select_episode
import lovehan1me.video.ui.speed
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

const val TAG_SELECT_EPISODE_ICON_BUTTON = "SelectEpisodeIconButton"
const val TAG_SPEED_SWITCHER_TEXT_BUTTON = "SpeedSwitcherTextButton"
const val TAG_SPEED_SWITCHER_DROPDOWN_MENU = "SpeedSwitcherDropdownMenu"
const val TAG_SPEED_SWITCHER_SLIDER = "SpeedSwitcherSlider"
const val TAG_SPEED_SWITCHER_VALUE_INDICATOR = "SpeedSwitcherValueIndicator"
const val TAG_DANMAKU_ICON_BUTTON = "DanmakuIconButton"
const val TAG_VIDEO_ASPECT_RATIO_SELECTOR_TEXT_BUTTON = "VideoAspectRatioTextButton"
const val TAG_VIDEO_ASPECT_RATIO_SELECTOR_DROPDOWN_MENU = "VideoAspectRatioDropdownMenu"

const val TAG_FULL_SCREEN_BUTTON = "FullScreenButton"

@Stable
object PlayerControllerDefaults {
    /**
     * 播放 / 暂停。
     */
    @Composable
    fun PlaybackIcon(
        isPlaying: () -> Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        IconButton(
            onClick = onClick,
            modifier,
        ) {
            if (isPlaying()) {
                Icon(Icons.Rounded.Pause, contentDescription = "Pause", Modifier.size(36.dp))
            } else {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", Modifier.size(36.dp))
            }
        }
    }

    /**
     * 弹幕开关。
     */
    @Composable
    fun DanmakuIcon(
        danmakuEnabled: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        IconButton(
            onClick = onClick,
            modifier.testTag(TAG_DANMAKU_ICON_BUTTON),
        ) {
            if (danmakuEnabled) {
                Icon(Icons.Rounded.Subtitles, contentDescription = stringResource(Res.string.player_disable_danmaku))
            } else {
                Icon(Icons.Rounded.SubtitlesOff, contentDescription = stringResource(Res.string.player_enable_danmaku))
            }
        }
    }

    @Composable
    fun AudioIcon(
        volume: Float,
        isMute: Boolean,
        maxValue: Float,
        onClick: () -> Unit,
        onchange: (Float) -> Unit,
        controllerState: PlayerControllerState,
        modifier: Modifier = Modifier,
    ) {
        val hoverInteraction = remember { MutableInteractionSource() }
        val isHovered by hoverInteraction.collectIsHoveredAsState()
        val audioIconRequester = remember { Any() }

        LaunchedEffect(true) {
            snapshotFlow { isHovered }.collect {
                controllerState.setRequestAlwaysOn(audioIconRequester, isHovered)
            }
        }
        Box(
            modifier = modifier.hoverable(hoverInteraction),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val iconButton = @Composable {
                IconButton(
                    onClick = onClick,
                ) {
                    when {
                        isMute -> {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeOff,
                                contentDescription = stringResource(Res.string.player_mute),
                            )
                        }

                        volume < 0.33f -> {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeMute,
                                contentDescription = stringResource(Res.string.player_gesture_volume),
                            )
                        }

                        volume < 0.66f -> {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeDown,
                                contentDescription = stringResource(Res.string.player_gesture_volume),
                            )
                        }

                        else -> {
                            Icon(
                                Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = stringResource(Res.string.player_gesture_volume),
                            )
                        }
                    }
                }
            }

            iconButton()

            Popup(
                alignment = Alignment.BottomCenter,
            ) {
                Surface(
                    modifier = Modifier
                        .hoverable(hoverInteraction)
                        .clip(shape = CircleShape),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AnimatedVisibility(
                            visible = isHovered && !isMute,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = volume.times(100).roundToInt().toString(),
                                    modifier = Modifier.padding(8.dp),
                                )
                                val colors = SliderDefaults.colors(
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface,
                                )
                                VerticalSlider(
                                    value = volume,
                                    onValueChange = onchange,
                                    modifier = Modifier.width(96.dp),
                                    thumb = {},
                                    colors = colors,
                                    track = { sliderState ->
                                        SliderDefaults.Track(
                                            colors = colors,
                                            enabled = true,
                                            sliderState = sliderState,
                                            thumbTrackGapSize = 0.dp,
                                        )
                                    },
                                    valueRange = 0f..maxValue,
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = isHovered && !isMute,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            iconButton()
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun NextEpisodeIcon(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        IconButton(
            onClick,
            modifier,
        ) {
            Icon(Icons.Rounded.SkipNext, stringResource(Res.string.player_next_episode), Modifier.size(36.dp))
        }
    }

    @Composable
    fun SelectEpisodeIcon(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        TextButton(
            onClick,
            modifier.testTag(TAG_SELECT_EPISODE_ICON_BUTTON),
            colors = ButtonDefaults.textButtonColors(
                contentColor = LocalContentColor.current,
            ),
        ) {
            Text(stringResource(Res.string.player_select_episode))
        }
    }

    /**
     * 进入 / 退出全屏。
     *
     * 图标方向和点击行为读同一个 [fullscreenState]，不可能对不上。
     */
    @Composable
    fun FullscreenIcon(
        fullscreenState: PlayerFullscreenState,
        modifier: Modifier = Modifier,
    ) {
        val isFullscreen = fullscreenState.isFullscreen
        val focusManager by rememberUpdatedState(LocalFocusManager.current)
        IconButton(
            onClick = remember(fullscreenState) { { fullscreenState.toggle() } },
            modifier.ifThen(needWorkaroundForFocusManager) {
                onFocusEvent {
                    if (it.hasFocus) {
                        focusManager.clearFocus()
                    }
                }
            }.testTag(TAG_FULL_SCREEN_BUTTON),
        ) {
            if (isFullscreen) {
                Icon(Icons.Rounded.FullscreenExit, contentDescription = "Exit Fullscreen", Modifier.size(32.dp))
            } else {
                Icon(Icons.Rounded.Fullscreen, contentDescription = "Enter Fullscreen", Modifier.size(32.dp))
            }
        }
    }

    /**
     * 当前倍速入口与 Slider 弹层。
     *
     * 入口始终显示规范化后的当前值（固定两位小数）；弹层只有一条水平 Slider，
     * 拖动期间实时预览，松手后提交最终值。
     */
    @Composable
    fun SpeedSwitcher(
        state: PlaybackSpeedControllerState,
        modifier: Modifier = Modifier,
        onExpandedChanged: (expanded: Boolean) -> Unit = {},
    ) {
        SpeedSwitcher(
            currentSpeed = state.currentSpeed,
            speedRange = state.speedRange,
            onPreviewSpeed = state::previewSpeed,
            onCommitSpeed = state::commitSpeed,
            modifier = modifier,
            onExpandedChanged = onExpandedChanged,
        )
    }

    @Composable
    fun SpeedSwitcher(
        currentSpeed: Float,
        speedRange: ClosedFloatingPointRange<Float>,
        onPreviewSpeed: (Float) -> Unit,
        onCommitSpeed: (Float) -> Unit,
        modifier: Modifier = Modifier,
        onExpandedChanged: (expanded: Boolean) -> Unit = {},
    ) {
        var expanded by rememberSaveable { mutableStateOf(false) }
        fun setExpanded(value: Boolean) {
            expanded = value
            onExpandedChanged(value)
        }

        Box(modifier, contentAlignment = Alignment.Center) {
            SpeedSwitcherButton(
                speed = currentSpeed,
                onClick = { setExpanded(true) },
            )

            if (expanded) {
                SpeedSliderPopup(
                    currentSpeed,
                    speedRange,
                    onPreviewSpeed,
                    onCommitSpeed,
                    onDismissRequest = { setExpanded(false) },
                )
            }
        }
    }

    @Composable
    private fun SpeedSwitcherButton(
        speed: Float,
        onClick: () -> Unit,
    ) {
        val speedText = stringResource(Res.string.speed)
        TextButton(
            onClick,
            colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
            modifier = Modifier.testTag(TAG_SPEED_SWITCHER_TEXT_BUTTON),
        ) {
            Text(remember(speed, speedText) { if (speed == 1.0f) speedText else """${speed.formatSpeedValue()}x""" })
        }
    }

    @Composable
    private fun SpeedSliderPopup(
        currentSpeed: Float,
        speedRange: ClosedFloatingPointRange<Float>,
        onPreviewSpeed: (Float) -> Unit,
        onCommitSpeed: (Float) -> Unit,
        onDismissRequest: () -> Unit,
    ) {
        Popup(
            popupPositionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                positioning = TooltipAnchorPosition.Above,
                spacingBetweenTooltipAndAnchor = 8.dp,
            ),
            onDismissRequest = onDismissRequest,
            properties = PlatformPopupProperties(focusable = true, clippingEnabled = false),
        ) {
            Surface(
                modifier = Modifier
                    .testTag(TAG_SPEED_SWITCHER_DROPDOWN_MENU)
                    .width(280.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
            ) {
                SteppedSlider(
                    value = currentSpeed,
                    onValueChange = onPreviewSpeed,
                    onValueChangeFinished = onCommitSpeed,
                    valueRange = speedRange,
                    valueIndicator = {
                        Text(
                            it.formatSpeedValue(),
                            Modifier.testTag(TAG_SPEED_SWITCHER_VALUE_INDICATOR),
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                    modifier = Modifier
                        .testTag(TAG_SPEED_SWITCHER_SLIDER)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }

    /**
     * 画面比例选择器。
     */
    @Composable
    fun VideoAspectRatioSelector(
        videoAspectRatioControllerState: VideoAspectRatioControllerState,
        modifier: Modifier = Modifier,
        onExpandedChanged: (expanded: Boolean) -> Unit = {},
    ) {
        return OptionsSwitcher(
            value = videoAspectRatioControllerState.currentMode,
            onValueChange = { videoAspectRatioControllerState.setMode(it) },
            optionsProvider = { VideoAspectRatioControllerState.Entries },
            renderValue = { Text(renderAspectRatioMode(it)) },
            renderValueExposed = { Text(renderAspectRatioMode(it)) },
            modifier,
            properties = PlatformPopupProperties(
                clippingEnabled = false,
            ),
            textButtonTestTag = TAG_VIDEO_ASPECT_RATIO_SELECTOR_TEXT_BUTTON,
            dropdownMenuTestTag = TAG_VIDEO_ASPECT_RATIO_SELECTOR_DROPDOWN_MENU,
            onExpandedChanged = onExpandedChanged,
        )
    }

    /**
     * @param optionsProvider 可选项。注意：选项内容变化不会反映到 UI 上。
     */
    @Composable
    fun <T> OptionsSwitcher(
        value: T,
        onValueChange: (T) -> Unit,
        optionsProvider: () -> List<T>,
        renderValue: @Composable (T) -> Unit,
        renderValueExposed: @Composable (T) -> Unit = renderValue,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        properties: PopupProperties = PopupProperties(),
        textButtonTestTag: String = "textButton",
        dropdownMenuTestTag: String = "dropDownMenu",
        onExpandedChanged: (expanded: Boolean) -> Unit = {},
    ) {
        Box(modifier, contentAlignment = Alignment.Center) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(true) {
                snapshotFlow { expanded }.collect {
                    onExpandedChanged(expanded)
                }
            }
            TextButton(
                { expanded = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = LocalContentColor.current,
                ),
                enabled = enabled,
                modifier = Modifier.testTag(textButtonTestTag),
            ) {
                renderValueExposed(value)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                properties = properties,
                modifier = Modifier.testTag(dropdownMenuTestTag),
            ) {
                val options = remember(optionsProvider) { optionsProvider() }
                for (option in options) {
                    DropdownMenuItem(
                        text = {
                            val color = if (value == option) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LocalContentColor.current
                            }
                            CompositionLocalProvider(LocalContentColor provides color) {
                                renderValue(option)
                            }
                        },
                        onClick = {
                            expanded = false
                            onValueChange(option)
                        },
                    )
                }
            }
        }
    }
}

/**
 * 播放器底部控件条。组件见 [PlayerControllerDefaults]。
 *
 * @param startActions [PlayerControllerDefaults.PlaybackIcon]、[PlayerControllerDefaults.DanmakuIcon]
 * @param progressIndicator [MediaProgressIndicatorText]
 * @param progressSlider [MediaProgressSlider]
 * @param danmakuEditor 弹幕控件位（开关 / 设置这类与发送无关的动作）。
 * @param endActions [PlayerControllerDefaults.FullscreenIcon]
 * @param expanded 是否展开。
 * `true` 时 [progressIndicator] 与 [progressSlider] 单独占一行，下一行放 [danmakuEditor]；
 * `false` 时整条只有一行，[danmakuEditor] 不显示。
 * @param sliderOnly 只保留 [progressSlider] 可见，其余占位但不可见（不换 composition）。
 */
@Composable
fun PlayerControllerBar(
    startActions: @Composable RowScope.() -> Unit,
    progressIndicator: @Composable RowScope.() -> Unit,
    progressSlider: @Composable RowScope.() -> Unit,
    danmakuEditor: @Composable RowScope.() -> Unit,
    endActions: @Composable RowScope.() -> Unit,
    expanded: Boolean,
    sliderOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clickable(remember { MutableInteractionSource() }, null, onClick = {}) // Consume touch event
            .padding(
                horizontal = if (expanded) 8.dp else 4.dp,
                vertical = if (expanded) 4.dp else 2.dp,
            ),
    ) {
        Column {
            ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                Row(
                    Modifier
                        .keepLayoutWhenHidden(sliderOnly)
                        .padding(start = if (expanded) 8.dp else 4.dp)
                        .padding(vertical = if (expanded) 4.dp else 2.dp),
                ) {
                    progressIndicator()
                }
                if (expanded) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        progressSlider()
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (expanded) 8.dp else 4.dp),
        ) {
            Row(
                Modifier.keepLayoutWhenHidden(sliderOnly),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                startActions()
            }

            Row(
                Modifier.weight(1f).keepLayoutWhenHidden(sliderOnly && expanded),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (expanded) {
                    ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                        danmakuEditor()
                    }
                } else {
                    progressSlider()
                }
            }

            Row(
                Modifier.keepLayoutWhenHidden(sliderOnly),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                endActions()
            }
        }
    }
}
