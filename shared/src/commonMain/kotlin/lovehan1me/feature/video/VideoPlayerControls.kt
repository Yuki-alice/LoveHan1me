package lovehan1me.feature.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.player_progress_percent
import lovehan1me.player_gesture_volume
import lovehan1me.player_gesture_progress
import lovehan1me.player_gesture_brightness
import lovehan1me.ic_volume_up
import lovehan1me.ic_light_mode
import lovehan1me.ic_fast_rewind
import lovehan1me.ic_fast_forward
import lovehan1me.feature.player.posterBlur
import lovehan1me.ui.theme.HanimeDefaults
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import lovehan1me.core.platform.currentEpochMillis
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import lovehan1me.video_loading_failed
import lovehan1me.gif_capture
import lovehan1me.screenshot
import lovehan1me.sure_to_delete
import lovehan1me.super_resolution_quality
import lovehan1me.super_resolution_performance
import lovehan1me.super_resolution_off
import lovehan1me.retry
import lovehan1me.replay
import lovehan1me.player_time_format
import lovehan1me.player_speed_format
import lovehan1me.player_play_from_beginning
import lovehan1me.player_auto_quality
import lovehan1me.player_anime4k_label
import lovehan1me.playback_finished
import lovehan1me.here_is_empty
import lovehan1me.edit
import lovehan1me.delete
import lovehan1me.confirm
import lovehan1me.cancel
import lovehan1me.ic_unlock
import lovehan1me.ic_refresh
import lovehan1me.ic_play_arrow
import lovehan1me.ic_pause
import lovehan1me.ic_lock
import lovehan1me.ic_home
import lovehan1me.ic_fullscreen
import lovehan1me.ic_arrow_back_ios
import lovehan1me.ui.component.FilledIconButton
import lovehan1me.ui.component.FilledTonalButton
import lovehan1me.ui.component.FilledTonalIconButton
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.ui.component.IconButton
import lovehan1me.feature.player.PlaybackEngine
import lovehan1me.feature.player.PlaybackQuality
import lovehan1me.feature.player.PlatformVideoSurface
import lovehan1me.feature.player.PlayerDefaults
import lovehan1me.ui.transition.sharedCoverElement
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.core.util.SonnerToast
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds


// M3：createGoogleCastIndicator / whiteDrawable 已随 Cast 按钮搬
// androidMain（ui.player.VideoPlatform.android.kt）。

/**
 * M3 官方硬指标：图标按钮的**触控目标**不得小于 48dp。
 *
 * 注意区分两件事：
 * - **视觉尺寸**：用户看到的图形（图标 18/20dp、锁钮圆底 42dp、chip 药丸高度）—— 本轮一律不动；
 * - **命中区**：真正吃掉点击/触摸的区域 —— 本轮统一补到 [PLAYER_MIN_TOUCH_TARGET]。
 */
internal val PLAYER_MIN_TOUCH_TARGET = HanimeDefaults.PlayerSizes.minTouchTarget

/**
 * 把可点控件的**命中区**撑到 [hit]（默认 M3 的 48dp），但**向上仍报告视觉尺寸**，
 * 因此父级排布（间距、容器高度、相邻控件位置）与改动前逐像素一致。
 *
 * 实现要点：
 * 1. 量子节点时把约束放宽到 ≥[hit]，这样被包裹的 clickable 自身边界就是 48dp ——
 *    M3 的 `minimumInteractiveComponentSize()` 只把**上报尺寸**撑到 48，
 *    被 `Modifier.size()` 夹住时 clickable 的边界并不会变大（M3 1.5 `MinimumInteractiveModifierNode`
 *    是用父级约束量子节点、再 `max(placeable, 48dp)` 上报），所以必须放行约束。
 * 2. 量完以后**上报**视觉尺寸（[visual]，或 [visual] 为空时取子节点在原始约束下的固有尺寸），
 *    并把 48dp 的命中盒居中放好 —— 于是：“盒子”变大了，“占位”没变。
 * 3. 超出上报尺寸的那部分命中区依然能收到指针事件：`NodeCoordinator.hitTest` 在
 *    `isPointerInBounds` 为假时会走 `hitNear`（触摸的最小命中区外扩）/ `speculativeHit`
 *    （“本节点没命中，但子节点可能命中”），最终 `hitTestChild` 仍会访问到那个 48dp 的 clickable。
 *    前提是祖先链上没有 `clip` / 裁剪型 graphicsLayer —— 播放器的顶栏、底栏、chip 均无。
 *
 * 已知取舍：底栏是 6+12(进度条)+30(按钮行)+6 = 54dp，装不下两个 48dp，所以底栏按钮的命中盒
 *   会向上压进进度条下沿 9dp（只在按钮的水平范围内），该范围内按钮优先。
 *   彻底消除需要把底栏抬到 ≥60dp（Media3 官方底栏就是 60dp）—— 那是视觉变更，本轮不做。
 *
 * @param visual 向上报告的视觉尺寸；为 null 时取子节点固有尺寸（供尺寸由文字决定的 chip 使用）。
 */
internal fun Modifier.playerHitTarget(
    hit: Dp = PLAYER_MIN_TOUCH_TARGET,
    visual: Dp? = null,
): Modifier = layout { measurable, constraints ->
    val hitPx = hit.roundToPx()
    // 1) 放宽约束：让内部 clickable 真的按 48dp 铺开
    val relaxed = constraints.copy(
        minWidth = maxOf(constraints.minWidth, hitPx),
        minHeight = maxOf(constraints.minHeight, hitPx),
        maxWidth = maxOf(constraints.maxWidth, hitPx),
        maxHeight = maxOf(constraints.maxHeight, hitPx),
    )
    // 2) 视觉尺寸：显式给定，或回落到子节点在原始约束下的固有尺寸
    val visualPx = visual?.roundToPx()
    val intrinsicWidth = measurable.minIntrinsicWidth(relaxed.maxHeight)
    val intrinsicHeight = measurable.minIntrinsicHeight(relaxed.maxWidth)
    val placeable = measurable.measure(relaxed)

    fun reported(measured: Int, intrinsic: Int, min: Int, max: Int): Int {
        val target = minOf(measured, visualPx ?: intrinsic)
        return maxOf(target, min).coerceAtMost(max)
    }
    val width = reported(placeable.width, measurable.minIntrinsicWidth(relaxed.maxHeight), constraints.minWidth, constraints.maxWidth)
    val height = reported(placeable.height, measurable.minIntrinsicHeight(relaxed.maxWidth), constraints.minHeight, constraints.maxHeight)

    // 3) 命中盒居中压在视觉尺寸上：视觉位置不变，多出来的部分向四周外扩
    layout(width, height) {
        placeable.place((width - placeable.width) / 2, (height - placeable.height) / 2)
    }
}

@Composable
internal fun PlayerMenuChip(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.medium
    // 命中层：M3 要求可点控件 ≥48dp。药丸外壳（视觉）保持原尺寸，多出来的命中区向外扩。
    Box(
        modifier = Modifier
            .playerHitTarget()
            .clip(shape)
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 视觉外壳：与改动前逐像素一致（clip / 底色 / 描边 / 内边距）
        Box(
            modifier = Modifier
                .clip(shape)
                .background(HanimeDefaults.Overlay.glass)
                .border(
                    1.dp,
                    HanimeDefaults.Overlay.border,
                    shape,
                )
                .padding(
                    horizontal = HanimeDefaults.Spacing.medium,
                    vertical = HanimeDefaults.Spacing.small,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = HanimeDefaults.Overlay.textSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** 双击左右跳转的步长（毫秒）。10 秒是 YouTube / 哔哩哔哩的通行值。 */
internal const val DOUBLE_TAP_SEEK_STEP_MS = 10_000L

/** 拖动进度条时的 seek 节流间隔（毫秒，M5-3）。 */
internal const val SLIDER_SEEK_THROTTLE_MS = 120L

/** 控件自动隐藏倒计时（毫秒，M5 体验打磨）：3s → 5s。 */
internal const val CONTROLS_AUTO_HIDE_MS = 5_000L

/** 画面缩放下限/上限（双指缩放 / Ctrl+滚轮）。 */
internal const val VIDEO_SCALE_MIN = 0.5f
internal const val VIDEO_SCALE_MAX = 4f

internal enum class PlayerSidePanel {
    Speed,
    SuperResolution,
    Quality,
}

/**
 * 面板选项列表：原自绘侧栏的**内容**原样搬进 M3 ModalBottomSheet。
 *
 * 单选高亮、选项来源与回调不变；行高补到 M3 的 48dp 触控目标；
 * 关闭统一「先播收起动画，动画结束后再摘状态」，避免面板瞬移消失。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSidePanelBottomSheet(
    options: List<String>,
    selectedIndex: Int?,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        // 返回键（含预测性返回）/ 点 scrim / 下滑手势都走这里 —— M3 自带 BackHandler
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HanimeDefaults.Spacing.small),
        ) {
            itemsIndexed(options) { index, option ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = HanimeDefaults.PlayerSizes.minTouchTarget)
                        .clickable {
                            // 选中即关：先播收起动画，动画结束再回调
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onSelected(index) }
                        }
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            }
                        )
                        .padding(vertical = 11.dp, horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            HanimeDefaults.Overlay.onScrim
                        },
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}


@Composable
fun PlayerSlider(
    value: Float,
    buffered: Float,
    onValueChange: (Float) -> Unit,
    /** 松手回调：调用方据此补一次最终 seek 并清掉本地乐观值。 */
    onValueChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {

    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,

        /**
         * Thumb
         */
        thumb = {
            Box(
                modifier = Modifier
                    .size(HanimeDefaults.PlayerSizes.thumbBox),
                contentAlignment = Alignment.Center
            ) {

                /**
                 * Glow
                 */
                Box(
                    modifier = Modifier
                        .size(HanimeDefaults.PlayerSizes.thumbGlow)
                        .background(
                            HanimeDefaults.Overlay.thumbGlow,
                            CircleShape
                        )
                )

                /**
                 * Real Thumb
                 */
                Box(
                    modifier = Modifier
                        .size(HanimeDefaults.PlayerSizes.thumb)
                        .background(
                            HanimeDefaults.Overlay.onScrim,
                            CircleShape
                        )
                )
            }
        },

        /**
         * Track
         */
        track = {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HanimeDefaults.PlayerSizes.trackBox),
                contentAlignment = Alignment.CenterStart
            ) {

                /**
                 * Background Track
                 */
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HanimeDefaults.PlayerSizes.track)
                        .clip(HanimeDefaults.Corners.pill)
                        .background(
                            HanimeDefaults.Overlay.track
                        )
                )

                /**
                 * Buffered Track
                 */
                Box(
                    modifier = Modifier
                        .fillMaxWidth(buffered.coerceIn(0f, 1f))
                        .height(HanimeDefaults.PlayerSizes.track)
                        .clip(HanimeDefaults.Corners.pill)
                        .background(
                            HanimeDefaults.Overlay.trackBuffered
                        )
                )

                /**
                 * Active Track
                 */
                Box(
                    modifier = Modifier
                        .fillMaxWidth(value.coerceIn(0f, 1f))
                        .height(HanimeDefaults.PlayerSizes.track)
                        .clip(HanimeDefaults.Corners.pill)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = HanimeDefaults.Alpha.secondary)
                                )
                            )
                        )
                )
            }
        }
    )
}

enum class GestureIndicatorType {
    Brightness,
    Volume,
    Progress,
}

internal enum class ProgressGestureDirection {
    Backward,
    Forward,
}

@Composable
internal fun GestureIndicatorOverlay(
    visible: Boolean,
    type: GestureIndicatorType,
    percent: Float,
    modifier: Modifier = Modifier,
    progressDirection: ProgressGestureDirection? = null,
    text: String? = null,
) {
    val displayText = text ?: stringResource(Res.string.player_progress_percent,
        (percent * 100).toInt(),
    )

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {

            /**
             * Glass Container
             */
            Box(
                modifier = Modifier
                    .size(
                        width = 170.dp,
                        height = 190.dp
                    )
                    .clip(MaterialTheme.shapes.extraLargeIncreased)
            ) {

                /**
                 * Blur Layer
                 */
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .posterBlur(55f)
                        .background(
                            HanimeDefaults.Overlay.blurDim
                        )
                )

                /**
                 * Glass Gradient
                 */
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    HanimeDefaults.Overlay.divider,
                                    HanimeDefaults.Overlay.textFaint
                                )
                            )
                        )
                )

                /**
                 * Border
                 */
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(
                            1.dp,
                            HanimeDefaults.Overlay.divider,
                            MaterialTheme.shapes.extraLargeIncreased
                        )
                )

                /**
                 * Content
                 */
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(HanimeDefaults.Spacing.extraLarge),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    Icon(
                        painter = when (type) {
                            GestureIndicatorType.Brightness -> painterResource(Res.drawable.ic_light_mode)
                            GestureIndicatorType.Volume -> painterResource(Res.drawable.ic_volume_up)
                            GestureIndicatorType.Progress -> when (progressDirection) {
                                ProgressGestureDirection.Backward -> painterResource(Res.drawable.ic_fast_rewind)
                                else -> painterResource(Res.drawable.ic_fast_forward)
                            }
                        },
                        contentDescription = null,
                        tint = HanimeDefaults.Overlay.onScrim,
                        modifier = Modifier.size(36.dp)
                    )

                    Spacer(modifier = Modifier.height(HanimeDefaults.Spacing.medium))

                    Text(
                        text = when (type) {
                            GestureIndicatorType.Brightness -> stringResource(Res.string.player_gesture_brightness)
                            GestureIndicatorType.Volume -> stringResource(Res.string.player_gesture_volume)
                            GestureIndicatorType.Progress -> stringResource(Res.string.player_gesture_progress)
                        },
                        color = HanimeDefaults.Overlay.textStrong,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(HanimeDefaults.Spacing.large))

                    LinearProgressIndicator(
                        progress = { percent.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(HanimeDefaults.Spacing.medium)
                            .clip(HanimeDefaults.Corners.pill),
                        trackColor = HanimeDefaults.Overlay.divider,
                    )

                    Spacer(modifier = Modifier.height(HanimeDefaults.Spacing.large))

                    Text(
                        text = displayText,
                        color = HanimeDefaults.Overlay.onScrim,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
        }
    }
}

// M3：设备时钟（原 DateFormat 系统格式）与单调时钟的跨平台实现。
internal fun nowMs(): Long = currentEpochMillis()

internal fun formatDeviceTime(epochMillis: Long): String {
    val dateTime = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = dateTime.hour.toString().padStart(2, '0')
    val minute = dateTime.minute.toString().padStart(2, '0')
    return "$hour:$minute"
}
