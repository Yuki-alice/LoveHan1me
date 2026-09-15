package lovehan1me.feature.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import lovehan1me.Res
import lovehan1me.cancel
import lovehan1me.confirm
import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.core.util.SonnerToast
import lovehan1me.delete
import lovehan1me.edit
import lovehan1me.feature.player.PlatformVideoSurface
import lovehan1me.feature.player.PlaybackEngine
import lovehan1me.feature.player.PlaybackQuality
import lovehan1me.feature.player.PlayerDefaults
import lovehan1me.feature.player.posterBlur
import lovehan1me.gif_capture
import lovehan1me.danmaku_input_hint
import lovehan1me.danmaku_send
import lovehan1me.here_is_empty
import lovehan1me.ic_arrow_back_ios
import lovehan1me.ic_fast_forward
import lovehan1me.ic_fast_rewind
import lovehan1me.ic_fullscreen
import lovehan1me.ic_home
import lovehan1me.ic_light_mode
import lovehan1me.ic_lock
import lovehan1me.ic_pause
import lovehan1me.ic_play_arrow
import lovehan1me.ic_refresh
import lovehan1me.ic_skip
import lovehan1me.ic_unlock
import lovehan1me.ic_volume_up
import lovehan1me.playback_finished
import lovehan1me.player_anime4k_label
import lovehan1me.player_auto_quality
import lovehan1me.player_gesture_brightness
import lovehan1me.player_gesture_progress
import lovehan1me.player_gesture_volume
import lovehan1me.player_play_from_beginning
import lovehan1me.player_progress_percent
import lovehan1me.player_speed_format
import lovehan1me.player_time_format
import lovehan1me.replay
import lovehan1me.retry
import lovehan1me.screenshot
import lovehan1me.super_resolution_off
import lovehan1me.super_resolution_performance
import lovehan1me.super_resolution_quality
import lovehan1me.sure_to_delete
import lovehan1me.temporarily_unavailable
import lovehan1me.ui.component.FilledIconButton
import lovehan1me.ui.component.FilledTonalButton
import lovehan1me.ui.component.FilledTonalIconButton
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.ui.component.IconButton
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.ui.transition.sharedCoverElement
import lovehan1me.video_loading_failed
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 播放器底部控制栏。
 *
 * 行结构复刻 animeko `PlayerControllerBar` —— **两态**，由 [expanded] 决定：
 * - **非 expanded**（窄屏竖屏）：`[时间行] / [播放… · 进度条 weight(1f) · …全屏]`
 *   进度条**内联在图标之间**，这是 animeko 窄屏的形态；
 * - **expanded**（宽屏双栏 / 全屏）：`[时间行] / [全宽进度条] / [操作行]`。
 *
 * 皮肤（[bilibiliStyle]）只改**背景与前景样式**，**不改行结构** ——
 * 两套皮肤走同一个骨架，避免"改一处行结构要改两遍"（见复刻方案 §十 风险项）。
 *
 * 从 `VideoPlayerUi` 主函数提取。底栏自己**不持有状态**：
 * 进度条的乐观值与 seek 节流留在调用方（VideoPlayerUi 主函数），
 * 这里只收 [sliderValue] 与两个滑动回调；开侧栏通过 onOpen*Panel 回调上抛。
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
    fullscreenEnabled: Boolean,
    onFullscreenClick: () -> Unit,
    onOpenSpeedPanel: () -> Unit,
    onOpenQualityPanel: () -> Unit,
    /**
     * Kazumi 哔哩哔哩风：底 scrim + 白图标/白字，去毛玻璃卡片，药丸改白字。
     * 只在宽屏/全屏由调用方打开，窄屏竖屏保持 false → 原分支逐像素不变。
     */
    bilibiliStyle: Boolean = false,
    /** 下一集（系列视频才有，null = 不显示）。只在 B 站风分支使用，窄屏不动。 */
    onNextClick: (() -> Unit)? = null,
    /** 视频总时长（毫秒）。> 0 时进度条才启用时间预览气泡与上滑取消 seek。 */
    durationMs: Long = 0L,
    /**
     * animeko 的「expanded」形态：宽屏双栏 / 全屏时为 true（进度条独占一行）。
     * 由 [VideoShellContent] 按 `isDualPane || isFullscreen` 传入。
     */
    expanded: Boolean = false,
    /**
     * 悬停交互源：**底栏容器**上的 hover 用来"请求控件常亮"。
     *
     * 复刻 animeko 的挂点 —— 它把 `Modifier.hoverToRequestAlwaysOn()` 挂在
     * `VideoScaffold` 的底栏槽位上（`VideoScaffold.kt:236`），不是整个播放器；
     * 否则"鼠标停在画面正中"也会让控件永不隐藏。
     */
    hoverInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    if (bilibiliStyle) {
        BilibiliBottomBar(
            visible = visible,
            sliderValue = sliderValue,
            bufferedProgress = bufferedProgress,
            onSliderValueChange = onSliderValueChange,
            onSliderValueChangeFinished = onSliderValueChangeFinished,
            isPlaying = isPlaying,
            onPlayClick = onPlayClick,
            currentTime = currentTime,
            totalTime = totalTime,
            playbackSpeed = playbackSpeed,
            resolvedQualityLabel = resolvedQualityLabel,
            fullscreenEnabled = fullscreenEnabled,
            onFullscreenClick = onFullscreenClick,
            onOpenSpeedPanel = onOpenSpeedPanel,
            onOpenQualityPanel = onOpenQualityPanel,
            onNextClick = onNextClick,
            durationMs = durationMs,
            expanded = expanded,
            hoverInteractionSource = hoverInteractionSource,
        )
        return
    }
/**
 * 底部控制栏（默认皮肤：毛玻璃卡）
 */
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
            // 悬停底栏 = 请求控件常亮
            .hoverable(hoverInteractionSource)
            .padding(
                horizontal = 18.dp,
                vertical = HanimeDefaults.Spacing.small,
            )
    ) {

        /**
         * Background —— 毛玻璃卡（组件样式保留，皮肤差异只在这一层）
         */
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(MaterialTheme.shapes.largeIncreased)
        ) {

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .posterBlur()
                    .background(
                        HanimeDefaults.Overlay.barSurface
                    )
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        1.dp,
                        HanimeDefaults.Overlay.border,
                        MaterialTheme.shapes.largeIncreased
                    )
            )
        }

        PlayerBottomBarContent(
            skin = PlayerBarSkin.Glass,
            expanded = expanded,
            sliderValue = sliderValue,
            bufferedProgress = bufferedProgress,
            onSliderValueChange = onSliderValueChange,
            onSliderValueChangeFinished = onSliderValueChangeFinished,
            durationMs = durationMs,
            isPlaying = isPlaying,
            onPlayClick = onPlayClick,
            currentTime = currentTime,
            totalTime = totalTime,
            playbackSpeed = playbackSpeed,
            resolvedQualityLabel = resolvedQualityLabel,
            fullscreenEnabled = fullscreenEnabled,
            onFullscreenClick = onFullscreenClick,
            onOpenSpeedPanel = onOpenSpeedPanel,
            onOpenQualityPanel = onOpenQualityPanel,
            onNextClick = onNextClick,
        )
    }
}
}

/**
 * Kazumi 非 compact 底栏对应物。
 *
 * 背景是底 scrim（Transparent → scrimBottomEnd 纵向渐变），无毛玻璃、无描边；
 * **行结构与默认皮肤完全一致**（共用 [PlayerBottomBarContent]），
 * 只把背景与前景样式换掉 —— 这正是复刻方案 §十 里"避免皮肤双分支膨胀"的做法。
 */
@Composable
private fun BoxScope.BilibiliBottomBar(
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
    fullscreenEnabled: Boolean,
    onFullscreenClick: () -> Unit,
    onOpenSpeedPanel: () -> Unit,
    onOpenQualityPanel: () -> Unit,
    onNextClick: (() -> Unit)?,
    durationMs: Long,
    expanded: Boolean,
    hoverInteractionSource: MutableInteractionSource,
) {
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
                // 悬停底栏 = 请求控件常亮（与默认皮肤同一语义）
                .hoverable(hoverInteractionSource)
        ) {
            // 底 scrim：matchParentSize 压在内容区上（Kazumi 是 h=100 的 black45 渐变；
            // 这里复用 Overlay scrim token，色值由 Defaults 统一收敛）。
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
            PlayerBottomBarContent(
                skin = PlayerBarSkin.Bilibili,
                expanded = expanded,
                sliderValue = sliderValue,
                bufferedProgress = bufferedProgress,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished,
                durationMs = durationMs,
                isPlaying = isPlaying,
                onPlayClick = onPlayClick,
                currentTime = currentTime,
                totalTime = totalTime,
                playbackSpeed = playbackSpeed,
                resolvedQualityLabel = resolvedQualityLabel,
                fullscreenEnabled = fullscreenEnabled,
                onFullscreenClick = onFullscreenClick,
                onOpenSpeedPanel = onOpenSpeedPanel,
                onOpenQualityPanel = onOpenQualityPanel,
                onNextClick = onNextClick,
            )
        }
    }
}

/**
 * 底栏皮肤。
 *
 * **只影响背景与前景样式，不影响行结构** —— 行结构由 [PlayerBottomBarContent] 单点决定，
 * 皮肤多了以后也不会出现"行结构 × 皮肤"的组合爆炸（复刻方案 §十 风险项）。
 */
private enum class PlayerBarSkin {
    /** 默认皮肤：毛玻璃卡 + 玻璃药丸 chip。 */
    Glass,

    /** Kazumi 哔哩哔哩风：底 scrim + 白图标/白字入口。 */
    Bilibili,
}

/**
 * 底栏内容骨架 —— **两套皮肤共用**，保证 [expanded] 的两态行结构逐行一致。
 *
 * 行结构对应 animeko `PlayerControllerBar(expanded, sliderOnly)`：
 * - `expanded = false`：`[时间行] / [startActions · slider(weight 1f) · endActions]`
 * - `expanded = true` ：`[时间行] / [全宽 slider] / [startActions · 中间位 · endActions]`
 *
 * 中间位在 animeko 是**弹幕输入框**；我们没有弹幕数据能力，
 * 因此 B 站风保留既有的「暂未开放」占位条（位置正确，且不撒谎），默认皮肤留空把两侧推到位。
 */
@Composable
private fun PlayerBottomBarContent(
    skin: PlayerBarSkin,
    expanded: Boolean,
    sliderValue: Float,
    bufferedProgress: Float,
    onSliderValueChange: (Float) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    durationMs: Long,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    currentTime: String,
    totalTime: String,
    playbackSpeed: Float,
    resolvedQualityLabel: String,
    fullscreenEnabled: Boolean,
    onFullscreenClick: () -> Unit,
    onOpenSpeedPanel: () -> Unit,
    onOpenQualityPanel: () -> Unit,
    onNextClick: (() -> Unit)?,
) {
    val timeText = stringResource(Res.string.player_time_format, currentTime, totalTime)

    Column(
        modifier = Modifier.padding(
            horizontal = HanimeDefaults.Spacing.large,
            vertical = if (skin == PlayerBarSkin.Glass) 6.dp else HanimeDefaults.Spacing.extraSmall,
        )
    ) {
        // ── 行 1：时间 ────────────────────────────────────────────────
        // animeko：`Row(padding start 4) { progressIndicator() }` —— 时间**独占一行**、
        // 靠左对齐，而不是夹在播放键与入口之间。
        Row(modifier = Modifier.padding(start = HanimeDefaults.Spacing.small)) {
            PlayerTimeText(text = timeText)
        }

        if (expanded) {
            // ── 行 2：全宽进度条独占一行 ───────────────────────────────
            PlayerSlider(
                value = sliderValue,
                buffered = bufferedProgress,
                onValueChange = onSliderValueChange,
                onValueChangeFinished = onSliderValueChangeFinished,
                durationMs = durationMs,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HanimeDefaults.PlayerSizes.bottomControlRow),
            )

            // ── 行 3：操作行（左操作 · 中间位 · 右操作）─────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HanimeDefaults.PlayerSizes.bottomControlRow),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.medium),
            ) {
                PlayerBarStartActions(
                    isPlaying = isPlaying,
                    onPlayClick = onPlayClick,
                    onNextClick = onNextClick,
                )

                if (skin == PlayerBarSkin.Bilibili) {
                    BiliDanmakuField(modifier = Modifier.weight(1f))
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                PlayerBarEndActions(
                    skin = skin,
                    playbackSpeed = playbackSpeed,
                    resolvedQualityLabel = resolvedQualityLabel,
                    fullscreenEnabled = fullscreenEnabled,
                    onOpenSpeedPanel = onOpenSpeedPanel,
                    onOpenQualityPanel = onOpenQualityPanel,
                    onFullscreenClick = onFullscreenClick,
                )
            }
        } else {
            // ── 非 expanded：进度条**内联**在图标之间（animeko 窄屏形态）──
            // 行高按 bottomControlRow(48dp) 而不是 bottomRow(30dp)：
            // 行内现在装着进度条，必须容得下它的命中区。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HanimeDefaults.PlayerSizes.bottomControlRow),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.small),
            ) {
                PlayerBarStartActions(
                    isPlaying = isPlaying,
                    onPlayClick = onPlayClick,
                    onNextClick = onNextClick,
                )

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

                PlayerBarEndActions(
                    skin = skin,
                    playbackSpeed = playbackSpeed,
                    resolvedQualityLabel = resolvedQualityLabel,
                    fullscreenEnabled = fullscreenEnabled,
                    onOpenSpeedPanel = onOpenSpeedPanel,
                    onOpenQualityPanel = onOpenQualityPanel,
                    onFullscreenClick = onFullscreenClick,
                )
            }
        }
    }
}

/** 底栏左操作组：播放/暂停（永远最左）+ 下一集。对应 animeko 的 `startActions`。 */
@Composable
private fun PlayerBarStartActions(
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onNextClick: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PlayerBarIconButton(
            drawable = if (isPlaying) Res.drawable.ic_pause else Res.drawable.ic_play_arrow,
            visual = HanimeDefaults.PlayerSizes.bottomPrimaryIcon,
            onClick = onPlayClick,
        )
        if (onNextClick != null) {
            PlayerBarIconButton(
                drawable = Res.drawable.ic_skip,
                visual = HanimeDefaults.PlayerSizes.bottomPrimaryIcon,
                onClick = onNextClick,
            )
        }
    }
}

/**
 * 底栏右操作组：倍速 · 清晰度 · 全屏（全屏永远最右）。对应 animeko 的 `endActions`。
 *
 * 皮肤差异只体现在"入口长什么样"：默认皮肤是玻璃药丸 [PlayerMenuChip]，
 * B 站风是白字 [BiliTextButton] —— 两者的**位置与顺序完全相同**。
 */
@Composable
private fun PlayerBarEndActions(
    skin: PlayerBarSkin,
    playbackSpeed: Float,
    resolvedQualityLabel: String,
    fullscreenEnabled: Boolean,
    onOpenSpeedPanel: () -> Unit,
    onOpenQualityPanel: () -> Unit,
    onFullscreenClick: () -> Unit,
) {
    val speedLabel = stringResource(Res.string.player_speed_format, playbackSpeed)

    Row(verticalAlignment = Alignment.CenterVertically) {
        when (skin) {
            PlayerBarSkin.Glass -> {
                PlayerMenuChip(label = speedLabel, onClick = onOpenSpeedPanel)
                Spacer(modifier = Modifier.width(6.dp))
                PlayerMenuChip(label = resolvedQualityLabel, onClick = onOpenQualityPanel)
            }

            PlayerBarSkin.Bilibili -> {
                BiliTextButton(label = speedLabel, onClick = onOpenSpeedPanel)
                Spacer(modifier = Modifier.width(6.dp))
                BiliTextButton(label = resolvedQualityLabel, onClick = onOpenQualityPanel)
            }
        }

        /**
         * Fullscreen —— 平台没实现全屏时不显示入口（iOS 目前未实现），
         * 免得按钮按下去什么都不发生（"状态撒谎"）。
         */
        if (fullscreenEnabled) {
            Spacer(modifier = Modifier.width(HanimeDefaults.Spacing.extraSmall))
            PlayerBarIconButton(
                drawable = Res.drawable.ic_fullscreen,
                visual = HanimeDefaults.PlayerSizes.bottomSecondaryIcon,
                onClick = onFullscreenClick,
            )
        }
    }
}

/**
 * 底栏图标按钮：**命中区 48dp（M3 硬指标）/ 视觉按 [visual]**。
 *
 * 走 [playerHitTarget]，因此向上报告的是视觉尺寸 —— 底栏行高与相邻控件位置由 [visual]
 * 决定，而不是被 48dp 撑开。
 */
@Composable
private fun PlayerBarIconButton(
    drawable: DrawableResource,
    visual: Dp,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .playerHitTarget(visual = visual)
            .size(PLAYER_MIN_TOUCH_TARGET)
    ) {
        Icon(
            painter = painterResource(drawable),
            contentDescription = null,
            tint = HanimeDefaults.Overlay.onScrim,
            modifier = Modifier.size(visual)
        )
    }
}

/**
 * 底栏时间文字。
 *
 * 抄 animeko `MediaProgressIndicatorText` 的两个可读性手法：
 * ① **等宽数字**（`tnum`）—— 时间跳动时不会左右抖；
 * ② **描边**—— 底栏压在亮画面上时白字依然读得出来。
 * 描边用「同一段文字画两遍」（先描边色 Stroke、再填充色），与 animeko 的 `TextWithBorder` 同法。
 */
@Composable
private fun PlayerTimeText(text: String) {
    val density = LocalDensity.current
    val baseStyle = MaterialTheme.typography.labelSmall.copy(
        fontFeatureSettings = "tnum",
    )
    // 描边线宽 = **字号 / 15**（animeko `TextWithBorder` 的取值，`width.toPx() / 15`）。
    // 不用固定 dp：底栏用的是 labelSmall(11sp)，固定 2dp 会把这行小字压得糊成一团。
    val strokeWidthPx = with(density) {
        // labelSmall 一定带 sp 字号；`isSp` 只是防住"主题被换成 Unspecified"的边界
        val size = baseStyle.fontSize
        (if (size.isSp) size else 12.sp).toPx() / 15f
    }

    Box {
        // 描边层
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
        // 填充层
        Text(
            text = text,
            color = HanimeDefaults.Overlay.textSecondary,
            style = baseStyle,
            maxLines = 1,
        )
    }
}

/**
 * Kazumi B 站风底栏的弹幕输入区：半透明白胶囊 + hint + 发送字样。
 *
 * 输入可键入；「发送」本期不接弹幕逻辑，点击提示暂未开放（与右栏旧占位条语义一致，
 * 但位置正确——在播放器底栏内，而不是右栏 tab 顶上）。
 */
@Composable
private fun BiliDanmakuField(modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    val haptic = rememberHapticFeedback()
    val unavailableMessage = stringResource(Res.string.temporarily_unavailable)

    Row(
        modifier = modifier
            .heightIn(min = 32.dp)
            .clip(MaterialTheme.shapes.large)
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = MaterialTheme.typography.labelMedium.copy(color = Color.White),
            cursorBrush = SolidColor(Color.White.copy(alpha = 0.8f)),
            singleLine = true,
            modifier = Modifier.weight(1f),
            decorationBox = { innerField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.danmaku_input_hint),
                            color = Color.White.copy(alpha = 0.45f),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerField()
                }
            },
        )
        Text(
            text = stringResource(Res.string.danmaku_send),
            color = Color.White.copy(alpha = if (text.isBlank()) 0.4f else 0.9f),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clickable {
                    if (text.isNotBlank()) {
                        haptic()
                        text = ""
                        SonnerToast.info(unavailableMessage)
                    }
                }
                .padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}
