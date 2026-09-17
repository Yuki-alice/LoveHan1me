package lovehan1me.feature.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lovehan1me.Res
import lovehan1me.ic_fullscreen
import lovehan1me.ic_fullscreen_exit
import lovehan1me.ic_pause
import lovehan1me.ic_play_arrow
import lovehan1me.ic_skip
import lovehan1me.danmaku_input_hint
import lovehan1me.danmaku_send
import lovehan1me.feature.player.PlaybackQuality
import lovehan1me.feature.player.PlayerDefaults
import lovehan1me.player_speed_format
import lovehan1me.speed
import lovehan1me.temporarily_unavailable
import lovehan1me.ui.component.IconButton
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.core.util.AppToast
import lovehan1me.ui.theme.HanimeDefaults
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 播放器底栏 —— 照抄 Kazumi 桌面端（`player_item_panel.dart` → `_wideControls`
 * + `PlayerTransportBar` 非 compact 分支）。
 *
 * 宽屏（expanded）单行结构（按钮之间**无间距**，48dp 按钮紧贴）：
 * `[播放 25dp][下一集 24dp][时间 16sp] [弹幕框(居中, max 500dp)] [超分辨率][倍速][清晰度][全屏 24dp]`
 * 上方一条全宽进度条，下方留 6dp。
 *
 * - 时间 inline 在 next 后面（Kazumi `afterPlaybackButtons`），16sp 全白等宽，无描边；
 * - 弹幕框：高 33dp、8dp 圆角、白 38% 底、15sp、右侧 `发送` 胶囊钮；
 *   没有弹幕后端，"发送"只提示暂未开放（输入框/按钮走关闭态配色）；
 * - 超分辨率 / 倍速 / 清晰度：白字 TextButton + Kazumi 式上方弹窗
 *   （取代此前的 BottomSheet；清晰度是 Hanime 刚需，原 Kazumi 该槽位是画面比例图标）；
 * - 窄屏（!expanded）保持现状：时间独占一行 + 单行内联进度条（此前已验证无误，不动）。
 */
@Composable
internal fun BoxScope.PlayerBottomBar(
    visible: Boolean,
    sliderValue: Float,
    bufferedProgress: Float,
    onSliderValueChange: (Float) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    currentTime: String,
    totalTime: String,
    playbackSpeed: Float,
    resolvedQualityLabel: String,
    qualities: List<PlaybackQuality> = emptyList(),
    qualitySelectedIndex: Int? = null,
    onQualitySelected: (Int) -> Unit = {},
    fullscreenEnabled: Boolean,
    onFullscreenClick: () -> Unit,
    onPlaybackSpeedSelected: (Float) -> Unit = {},
    superResolutionOptions: List<String> = emptyList(),
    selectedSuperResolutionIndex: Int = 0,
    onSuperResolutionSelected: (Int) -> Unit = {},
    /** 下一集（系列视频才有，null = 不显示）。 */
    onNextClick: (() -> Unit)? = null,
    /** 是否全屏：全屏键图标随状态切换。 */
    isFullscreen: Boolean = false,
    /** 视频总时长（毫秒）。> 0 时进度条才启用时间预览气泡与上滑取消 seek。 */
    durationMs: Long = 0L,
    /**
     * 宽屏双栏 / 全屏时为 true（Kazumi 桌面行结构）。
     * 由 [VideoShellContent] 按 `isDualPane || isFullscreen` 传入。
     */
    expanded: Boolean = false,
    /** 有弹窗打开时通知调用方（控件自动隐藏要给弹窗让路，对齐 Kazumi 的 panel hold）。 */
    onMenuOpenChange: (Boolean) -> Unit = {},
    /**
     * 悬停交互源：**底栏容器**上的 hover 用来"请求控件常亮"。
     */
    hoverInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val speedLabel = if (playbackSpeed == 1f) {
        stringResource(Res.string.speed)
    } else {
        stringResource(Res.string.player_speed_format, playbackSpeed)
    }
    // 三个弹窗共用一个计数：任一打开就 hold 住控件（Kazumi `acquirePlayerPanelHold` 同法）。
    var openMenuCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(openMenuCount) {
        onMenuOpenChange(openMenuCount > 0)
    }
    fun menuOpenChanged(open: Boolean) {
        openMenuCount = (openMenuCount + if (open) 1 else -1).coerceAtLeast(0)
    }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                // 悬停底栏 = 请求控件常亮（与顶栏同一语义）
                .hoverable(hoverInteractionSource)
        ) {
            // 底 scrim：matchParentSize 压在内容区上。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                HanimeDefaults.Overlay.scrimBottomStart,
                                HanimeDefaults.Overlay.scrimBottomEnd,
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    // 吞掉触摸：点底栏空白处不触发播放器的"显示/隐藏控件"切换。
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                if (expanded) {
                    // ── 进度条独占一行（左右各 10dp）──────────────────
                    PlayerSlider(
                        value = sliderValue,
                        buffered = bufferedProgress,
                        onValueChange = onSliderValueChange,
                        onValueChangeFinished = onSliderValueChangeFinished,
                        durationMs = durationMs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                    )
                    // ── 按钮行（无间距，48dp 按钮紧贴，左右各 10dp，下方 6dp）──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onPlayClick) {
                            Icon(
                                painter = painterResource(if (isPlaying) Res.drawable.ic_pause
                                    else Res.drawable.ic_play_arrow),
                                contentDescription = null,
                                tint = HanimeDefaults.Overlay.onScrim,
                                modifier = Modifier.size(25.dp)
                            )
                        }
                        if (onNextClick != null) {
                            IconButton(onClick = onNextClick) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_skip),
                                    contentDescription = null,
                                    tint = HanimeDefaults.Overlay.onScrim,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Text(
                            text = "$currentTime / $totalTime",
                            color = HanimeDefaults.Overlay.onScrim,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                fontFeatureSettings = "tnum",
                            ),
                            maxLines = 1,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                        // 中间位：弹幕框居中、max 500dp（Kazumi `_desktopDanmakuControls` 同法）。
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            KazumiDanmakuField(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 500.dp),
                            )
                        }
                        KazumiTextMenu(
                            label = superResolutionOptions.getOrNull(
                                selectedSuperResolutionIndex
                            ) ?: superResolutionOptions.firstOrNull().orEmpty(),
                            options = superResolutionOptions,
                            selectedIndex = selectedSuperResolutionIndex,
                            onSelected = onSuperResolutionSelected,
                            onOpenChange = ::menuOpenChanged,
                        )
                        KazumiTextMenu(
                            label = speedLabel,
                            options = PlayerDefaults.speeds.map {
                                stringResource(Res.string.player_speed_format, it)
                            },
                            selectedIndex = PlayerDefaults.speeds.indexOfFirst {
                                it == playbackSpeed
                            }.takeIf { it >= 0 },
                            onSelected = { index ->
                                onPlaybackSpeedSelected(PlayerDefaults.speeds[index])
                            },
                            onOpenChange = ::menuOpenChanged,
                        )
                        KazumiTextMenu(
                            label = resolvedQualityLabel,
                            options = qualities.map { it.label },
                            selectedIndex = qualitySelectedIndex,
                            onSelected = onQualitySelected,
                            onOpenChange = ::menuOpenChanged,
                        )
                        if (fullscreenEnabled) {
                            IconButton(onClick = onFullscreenClick) {
                                Icon(
                                    painter = painterResource(if (isFullscreen) Res.drawable.ic_fullscreen_exit
                                        else Res.drawable.ic_fullscreen),
                                    contentDescription = null,
                                    tint = HanimeDefaults.Overlay.onScrim,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                } else {
                    // ── 窄屏：保持现状（时间独占一行 + 单行内联进度条）────────
                    ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                        Row(
                            modifier = Modifier.padding(
                                start = 4.dp,
                                top = 2.dp,
                                bottom = 2.dp
                            ),
                        ) {
                            PlayerTimeText(currentTime = currentTime, totalTime = totalTime)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(HanimeDefaults.PlayerSizes.bottomControlRow)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onPlayClick) {
                                Icon(
                                    painter = painterResource(if (isPlaying) Res.drawable.ic_pause
                                        else Res.drawable.ic_play_arrow),
                                    contentDescription = null,
                                    tint = HanimeDefaults.Overlay.onScrim,
                                    modifier = Modifier.size(HanimeDefaults.PlayerSizes.bottomPrimaryIcon)
                                )
                            }
                            if (onNextClick != null) {
                                IconButton(onClick = onNextClick) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_skip),
                                        contentDescription = null,
                                        tint = HanimeDefaults.Overlay.onScrim,
                                        modifier = Modifier.size(HanimeDefaults.PlayerSizes.bottomPrimaryIcon)
                                    )
                                }
                            }
                        }
                        PlayerSlider(
                            value = sliderValue,
                            buffered = bufferedProgress,
                            onValueChange = onSliderValueChange,
                            onValueChangeFinished = onSliderValueChangeFinished,
                            durationMs = durationMs,
                            modifier = Modifier
                                .weight(1f)
                                .height(HanimeDefaults.PlayerSizes.bottomControlRow),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KazumiTextMenu(
                                label = speedLabel,
                                options = PlayerDefaults.speeds.map {
                                    stringResource(Res.string.player_speed_format, it)
                                },
                                selectedIndex = PlayerDefaults.speeds.indexOfFirst {
                                    it == playbackSpeed
                                }.takeIf { it >= 0 },
                                onSelected = { index ->
                                    onPlaybackSpeedSelected(PlayerDefaults.speeds[index])
                                },
                                onOpenChange = ::menuOpenChanged,
                            )
                            KazumiTextMenu(
                                label = resolvedQualityLabel,
                                options = qualities.map { it.label },
                                selectedIndex = qualitySelectedIndex,
                                onSelected = onQualitySelected,
                                onOpenChange = ::menuOpenChanged,
                            )
                        }
                        if (fullscreenEnabled) {
                            IconButton(onClick = onFullscreenClick) {
                                Icon(
                                    painter = painterResource(if (isFullscreen) Res.drawable.ic_fullscreen_exit
                                        else Res.drawable.ic_fullscreen),
                                    contentDescription = null,
                                    tint = HanimeDefaults.Overlay.onScrim,
                                    modifier = Modifier.size(HanimeDefaults.PlayerSizes.bottomSecondaryIcon)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 底栏时间文字（窄屏行）。
 *
 * 白字 + 深灰描边（同一段文字画两遍：先描边色 Stroke、再填充色），
 * 描边线宽 = **字号 / 15**。等宽数字（`tnum`）保证跳动不抖。
 */
@Composable
private fun PlayerTimeText(currentTime: String, totalTime: String) {
    val density = LocalDensity.current
    val baseStyle = MaterialTheme.typography.labelMedium.copy(
        fontFeatureSettings = "tnum",
    )
    val strokeWidthPx = with(density) {
        val size = baseStyle.fontSize
        (if (size.isSp) size else 12.sp).toPx() / 15f
    }
    @Composable
    fun StrokedText(text: String, fill: Color) {
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
            )
            Text(
                text = text,
                color = fill,
                style = baseStyle,
                maxLines = 1,
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        StrokedText(text = currentTime, fill = HanimeDefaults.Overlay.onScrim)
        StrokedText(
            text = " / $totalTime",
            fill = HanimeDefaults.Overlay.onScrim.copy(alpha = 0.618f),
        )
    }
}

/**
 * Kazumi 式弹幕输入区（宽屏中间位）：高 33dp、8dp 圆角、白 38% 底、
 * 15sp 白字、右侧 `发送` 胶囊钮（primaryContainer 底）。
 *
 * 没有弹幕后端：输入框恒为关闭态配色，「发送」只提示暂未开放。
 */
@Composable
private fun KazumiDanmakuField(modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    val haptic = rememberHapticFeedback()
    val unavailableMessage = stringResource(Res.string.temporarily_unavailable)

    Row(
        modifier = modifier
            .height(33.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.38f))
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                color = HanimeDefaults.Overlay.onScrim.copy(alpha = 0.6f),
            ),
            cursorBrush = SolidColor(HanimeDefaults.Overlay.onScrim.copy(alpha = 0.8f)),
            singleLine = true,
            modifier = Modifier.weight(1f),
            decorationBox = { innerField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.danmaku_input_hint),
                            color = HanimeDefaults.Overlay.onScrim.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerField()
                }
            },
        )
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(
            onClick = {
                if (text.isNotBlank()) {
                    haptic()
                    text = ""
                    AppToast.info(unavailableMessage)
                }
            },
            colors = ButtonDefaults.textButtonColors(
                contentColor = HanimeDefaults.Overlay.onScrim.copy(alpha = 0.6f),
            ),
        ) {
            Text(
                text = stringResource(Res.string.danmaku_send),
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
