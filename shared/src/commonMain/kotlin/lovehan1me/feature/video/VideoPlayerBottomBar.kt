package lovehan1me.feature.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lovehan1me.Res
import lovehan1me.ic_fullscreen
import lovehan1me.ic_fullscreen_exit
import lovehan1me.ic_pause
import lovehan1me.ic_play_arrow
import lovehan1me.ic_skip
import lovehan1me.feature.player.PlaybackQuality
import lovehan1me.feature.player.PlayerDefaults
import lovehan1me.player_speed_format
import lovehan1me.speed
import lovehan1me.ui.component.IconButton
import lovehan1me.ui.theme.HanimeDefaults
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 播放器底栏 —— 照抄 Kazumi 桌面端（`player_item_panel.dart` → `_wideControls`
 * + `PlayerTransportBar` 非 compact 分支）。
 *
 * 宽屏（expanded）单行结构（按钮之间**无间距**，48dp 按钮紧贴）：
 * `[播放 25dp][下一集 24dp][时间 16sp] [弹幕状态条(居中)] [超分辨率][倍速][清晰度][全屏 24dp]`
 * 上方一条全宽进度条，下方留 6dp。
 *
 * - 时间 inline 在 next 后面（Kazumi `afterPlaybackButtons`），16sp 全白等宽，无描边；
 * - 中间位是 [danmakuControls] 插槽（由屏幕边界给，通常是 `DanmakuControls` 双钮）。
 *   此前这里是自建的假输入框 + "发送"钮（点了只提示暂未开放），已随弹幕接入删除；
 *   再之前是状态胶囊 —— 状态要说的话搬进了设置弹窗的"状况"段，中间位只留动作。
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
    /**
     * 弹幕双钮插槽（宽屏底栏中间位，原假输入框的位置）。null = 中间位留空。
     *
     * 窄屏不画它：窄屏底栏没有中间位，且弹幕语义（B 站风宽屏）本就与窄屏皮肤无关。
     */
    danmakuControls: (@Composable () -> Unit)? = null,
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
                        // 中间位：弹幕双钮（开关 + 设置；状态与选集收进设置弹窗）。
                        // 这里原来是「KazumiDanmakuField」假输入框：v1 不发弹幕，它能做的只有
                        // 弹一句"暂未开放"，那是假动作，所以整块换掉。
                        // 宽度不必另设上限：weight(1f) 已经限死这一格，居中子项撑不过它。
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            danmakuControls?.let { controls -> controls() }
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
