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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SliderDefaults
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
import lovehan1me.ic_fast_forward
import lovehan1me.ic_fast_rewind
import lovehan1me.ic_light_mode
import lovehan1me.ic_volume_down
import lovehan1me.ic_volume_off
import lovehan1me.ic_volume_up
import lovehan1me.player_progress_percent
import lovehan1me.player_gesture_volume
import lovehan1me.player_gesture_progress
import lovehan1me.player_seek_release_to_cancel
import lovehan1me.player_gesture_brightness
// ⚠️ 必须**显式**导入：同包的 `VideoRouteHostScreen.kt` 里有一个 file-private 的
// `formatPlaybackTime`，同名会让解析器选中它并报"private in file"。
// 显式导入的优先级高于同包声明，因此这一行是必需的，不是冗余。
import lovehan1me.feature.player.formatPlaybackTime
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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
import lovehan1me.core.util.AppToast
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
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

/** 双击左右跳转的步长（毫秒）。10 秒是 YouTube / 哔哩哔哩的通行值。 */
internal const val DOUBLE_TAP_SEEK_STEP_MS = 10_000L

/** 拖动进度条时的 seek 节流间隔（毫秒，M5-3）。 */
internal const val SLIDER_SEEK_THROTTLE_MS = 120L

/** 控件自动隐藏倒计时（毫秒，M5 体验打磨）：3s → 5s。 */
internal const val CONTROLS_AUTO_HIDE_MS = 5_000L

/** 画面缩放下限/上限（双指缩放 / Ctrl+滚轮）。 */
internal const val VIDEO_SCALE_MIN = 0.5f
internal const val VIDEO_SCALE_MAX = 4f

/** 进度条时间预览气泡离进度条上沿的间距。 */
private val SliderPreviewGap = 6.dp

/**
 * 「上滑取消 seek」的竖直阈值：累计上滑超过这个距离就认为用户想放弃这次拖动。
 *
 * 48dp 是一个拇指容易表达、又不容易被误触的距离（与 M3 触控目标同档）。
 */
private val SeekCancelThreshold = 48.dp

/**
 * 时间预览气泡的位置：贴在进度条**上沿**、横向跟随指针 / thumb，并在窗口内夹住不越界。
 *
 * 为什么用 `Popup` 而不是把气泡画在底栏里：气泡需要"溢出到进度条上方"，
 * 而底栏外面套着 `AnimatedVisibility`，画在内部的浮层会被宿主的布局边界约束住。
 * `Popup` 以**父布局**为锚点、不受宿主裁剪影响，正好够用（animeko 也是这么做的）。
 */
private class SliderPreviewPositionProvider(
    private val ratio: Float,
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // 气泡中心对准锚点内的对应比例位置，再整体左移半个气泡宽
        val anchorX = anchorBounds.left +
            (anchorBounds.width * ratio.coerceIn(0f, 1f)).roundToInt()
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        return IntOffset(
            x = (anchorX - popupContentSize.width / 2).coerceIn(0, maxX),
            y = (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0),
        )
    }
}

/**
 * 进度条时间预览气泡（animeko `ProgressSliderPreviewPopup` 的**无帧形态**）：
 * 深底胶囊 + 时间文字。有帧预览（160×90 圆角矩形）需要引擎提供抓帧，
 * 见 `frameCaptureEnabled` —— 等接上再做，这里先把时间气泡立起来。
 */
@Composable
private fun SliderPreviewBubble(text: String) {
    Text(
        text = text,
        color = HanimeDefaults.Overlay.onScrim,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        modifier = Modifier
            .clip(HanimeDefaults.Corners.pill)
            .background(HanimeDefaults.Overlay.previewBubble)
            .border(1.dp, HanimeDefaults.Overlay.border, HanimeDefaults.Corners.pill)
            .padding(
                horizontal = HanimeDefaults.Spacing.medium,
                vertical = HanimeDefaults.Spacing.small,
            ),
    )
}

/**
 * 播放器进度条。
 *
 * 复刻 animeko `MediaProgressSlider` 的**交互面**（轨道/thumb 形制仍是我们自己的样式）：
 * - **时间预览气泡**：hover（指针设备）或拖动时贴着进度条上沿弹出，跟随指针 / thumb；
 * - **上滑取消 seek**：拖动中向上滑过 [SeekCancelThreshold] 即放弃本次拖动，
 *   松手回到拖动起点（animeko 的 `TouchSeekState` 的 Cancelling 语义）。
 *
 * 两条新能力的默认值都是"关"（`durationMs = 0`），所以老调用点行为零变化。
 *
 * @param durationMs 视频总时长（毫秒）。`> 0` 才启用预览气泡与上滑取消；
 *   `0` 时本组件与改动前逐像素一致。
 */
@Composable
fun PlayerSlider(
    value: Float,
    buffered: Float,
    onValueChange: (Float) -> Unit,
    /** 松手回调：调用方据此补一次最终 seek 并清掉本地乐观值。 */
    onValueChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    durationMs: Long = 0L,
) {
    // 观察层永远只读"最新值"，因此这两个状态不参与 pointerInput 的重启 key ——
    // 否则拖动期间 value 每帧都变，手势协程会被反复取消重启。
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)

    /** 拖动起点。上滑取消时回吐它，让调用方把最后一个值提交成"起点"。 */
    var dragStartValue by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isCancellingSeek by remember { mutableStateOf(false) }
    /** 悬停预览位置（0..1）。触摸端不产生 Enter/Move/Exit 悬停事件 → 恒为 null。 */
    var hoverRatio by remember { mutableStateOf<Float?>(null) }

    val density = LocalDensity.current
    val cancelThresholdPx = with(density) { SeekCancelThreshold.roundToPx() }
    val bubbleGapPx = with(density) { SliderPreviewGap.roundToPx() }

    // 气泡作为 Box 的子节点 → Popup 的锚点就是"进度条本身"，横向跟随才有意义。
    Box(
        modifier = modifier
            // ── 悬停跟踪（只读，不消费事件）──────────────────────────────
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        when (event.type) {
                            PointerEventType.Move -> {
                                val x = event.changes.lastOrNull()?.position?.x
                                hoverRatio = if (x != null && size.width > 0) {
                                    (x / size.width).coerceIn(0f, 1f)
                                } else {
                                    null
                                }
                            }
                            // 指针离开（触摸端抬手后 Compose 也会补一个 Exit）→ 收气泡
                            PointerEventType.Exit -> hoverRatio = null
                            else -> Unit
                        }
                    }
                }
            }
            // ── 上滑取消 seek（只读，不消费事件）────────────────────────
            // 走 Initial pass：比 M3 Slider 内部的 draggable（Main pass）更早看到抬手，
            // 因此"回吐起点值"一定发生在 Slider 的 onValueChangeFinished 之前。
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        dragStartValue = latestValue
                        isDragging = true
                        isCancellingSeek = false
                        var accumulatedDy = 0f
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            // 手算增量而不用 positionChange()：后者在新版 Compose 里
                            // 已被属性取代（不再是函数），position/previousPosition 是长期稳定的。
                            accumulatedDy += change.position.y - change.previousPosition.y
                            isCancellingSeek = accumulatedDy <= -cancelThresholdPx
                            if (!change.pressed) break
                        }
                        // 取消 = 回到起点：把起点值当成"最后一个值"回吐，
                        // 调用方的 onValueChangeFinished 会把它提交给引擎。
                        if (isCancellingSeek) latestOnValueChange(dragStartValue)
                        isDragging = false
                        isCancellingSeek = false
                    }
                }
            },
    ) {

        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            // 只占满宽度、高度取 Slider 自身固有高度（≈48dp 触控行高）：
            // `fillMaxSize` 在自适应高度的父容器里会一路吃到根的最大高度，
            // 把整列撑爆、操作行挤出屏幕（离屏渲染实测 slider 高达 936px、controlRow 高 0）。
            modifier = Modifier.fillMaxWidth(),

            /**
             * Thumb —— 对齐 animeko（primary 实心圆，直径 16dp，无光晕）。
             */
            thumb = {
                Box(
                    modifier = Modifier
                        .size(HanimeDefaults.PlayerSizes.thumbBox),
                    contentAlignment = Alignment.Center
                ) {
                    /**
                     * Real Thumb
                     */
                    Box(
                        modifier = Modifier
                            .size(HanimeDefaults.PlayerSizes.thumb)
                            .background(
                                MaterialTheme.colorScheme.primary,
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
                     * Active Track —— 对齐 animeko（纯 primary，无渐变）。
                     */
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(value.coerceIn(0f, 1f))
                            .height(HanimeDefaults.PlayerSizes.track)
                            .clip(HanimeDefaults.Corners.pill)
                            .background(
                                MaterialTheme.colorScheme.primary
                            )
                    )
                }
            }
        )

        // ── 时间预览气泡 ────────────────────────────────────────────────
        // 拖动中跟 thumb（`value` 是乐观值），悬停时跟指针。两者同时刻只有一个为真。
        val previewRatio = if (isDragging) value.coerceIn(0f, 1f) else hoverRatio
        if (durationMs > 0L && previewRatio != null) {
            Popup(
                popupPositionProvider = SliderPreviewPositionProvider(previewRatio, bubbleGapPx),
                // 浮层不吃焦点：它跟着鼠标跑，抢焦点会把 hover 打断
                properties = PopupProperties(focusable = false),
            ) {
                SliderPreviewBubble(
                    text = if (isCancellingSeek) {
                        stringResource(Res.string.player_seek_release_to_cancel)
                    } else {
                        formatPlaybackTime((previewRatio * durationMs).toLong())
                    },
                )
            }
        }
    }
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
    /**
     * Kazumi 哔哩哔哩风：顶部小 HUD（实色卡、无模糊），替代中央 170x190 大玻璃卡。
     * 只在宽屏/全屏由调用方打开，窄屏竖屏保持 false → 原分支逐像素不变。
     */
    bilibiliStyle: Boolean = false,
) {
    val displayText = text ?: stringResource(Res.string.player_progress_percent,
        (percent * 100).toInt(),
    )

    if (bilibiliStyle) {
        AnimatedVisibility(
            visible = visible,
            modifier = modifier,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                // Kazumi PlayerSeek/Speed/AdjustmentHud：top:25 小卡，surfaceContainerHighest
                // 实色 + 阴影，刻意不用 BackdropFilter/模糊（Impeller 下模糊有坑，且 B 站本就没有）。
                Surface(
                    modifier = Modifier.padding(top = 72.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = HanimeDefaults.Spacing.large,
                            vertical = HanimeDefaults.Spacing.medium,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.medium),
                    ) {
                        Icon(
                            painter = painterResource(when (type) {
                                GestureIndicatorType.Brightness -> Res.drawable.ic_light_mode
                                GestureIndicatorType.Volume -> Res.drawable.ic_volume_up
                                GestureIndicatorType.Progress -> when (progressDirection) {
                                    ProgressGestureDirection.Backward -> Res.drawable.ic_fast_rewind
                                    else -> Res.drawable.ic_fast_forward
                                }
                            }),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = when (type) {
                                GestureIndicatorType.Brightness -> stringResource(Res.string.player_gesture_brightness)
                                GestureIndicatorType.Volume -> stringResource(Res.string.player_gesture_volume)
                                GestureIndicatorType.Progress -> stringResource(Res.string.player_gesture_progress)
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = displayText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
        return
    }

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
                        painter = painterResource(when (type) {
                            GestureIndicatorType.Brightness -> Res.drawable.ic_light_mode
                            GestureIndicatorType.Volume -> Res.drawable.ic_volume_up
                            GestureIndicatorType.Progress -> when (progressDirection) {
                                ProgressGestureDirection.Backward -> Res.drawable.ic_fast_rewind
                                else -> Res.drawable.ic_fast_forward
                            }
                        }),
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

// ── Kazumi 式功能选择弹窗 ──────────────────────────────────────────────
// 对应 Kazumi `PlayerPanelHoldMenuAnchor` + `_menuLabel`：白字触发按钮 +
// 锚定在按钮上方的选择卡（圆角 12dp、行高 48dp、min 112dp、选中项 primary 色）。
// 取代此前的 BottomSheet（`PlayerSidePanel*` 已删除）：点外部关闭，选中即关。

/** 锚定位置：弹窗贴在锚点上方、右对齐，窗口内夹住不越界。 */
private class MenuAboveAnchorProvider(
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(
            x = (anchorBounds.right - popupContentSize.width).coerceIn(0, maxX),
            y = (anchorBounds.top - popupContentSize.height - gapPx).coerceIn(0, maxY),
        )
    }
}

/** 锚定位置：弹窗贴在锚点下方、右对齐（顶栏更多菜单用）。 */
private class MenuBelowAnchorProvider(
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(
            x = (anchorBounds.right - popupContentSize.width).coerceIn(0, maxX),
            y = (anchorBounds.bottom + gapPx).coerceIn(0, maxY),
        )
    }
}

@Composable
internal fun KazumiMenuPopup(
    above: Boolean,
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val gapPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    Popup(
        popupPositionProvider = if (above) {
            MenuAboveAnchorProvider(gapPx)
        } else {
            MenuBelowAnchorProvider(gapPx)
        },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .widthIn(min = 112.dp)
                    .padding(vertical = 8.dp),
                content = content,
            )
        }
    }
}

@Composable
internal fun androidx.compose.foundation.layout.ColumnScope.KazumiMenuOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptic = rememberHapticFeedback()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable {
                haptic()
                onClick()
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
        )
    }
}

/**
 * 白字文字触发钮 + 上方弹窗（底栏超分辨率 / 倍速 / 清晰度入口）。
 * 尺寸对 Flutter TextButton 默认：高 40dp、min-width 64dp。
 */
@Composable
internal fun RowScope.KazumiTextMenu(
    label: String,
    options: List<String>,
    selectedIndex: Int?,
    onSelected: (Int) -> Unit,
    onOpenChange: (Boolean) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    val haptic = rememberHapticFeedback()
    fun setOpen(value: Boolean) {
        open = value
        onOpenChange(value)
    }
    // 注意：Popup 必须放在锚点 Box **内部** —— PopupPositionProvider 收到的
    // anchorBounds 永远是 Popup 父布局的 bounds，传外部量到的 rect 进去是没用的
    // （之前量了 boundsInWindow 却没用上，弹窗相对整行定位导致整体偏移）。
    Box {
        androidx.compose.material3.TextButton(
            onClick = {
                haptic()
                setOpen(true)
            },
            modifier = Modifier
                .height(40.dp)
                .widthIn(min = 64.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = HanimeDefaults.Overlay.onScrim,
            ),
        ) {
            Text(
                text = label,
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (open) {
            KazumiMenuPopup(
                above = true,
                onDismiss = { setOpen(false) },
            ) {
                options.forEachIndexed { index, option ->
                    KazumiMenuOption(
                        label = option,
                        selected = index == selectedIndex,
                        onClick = {
                            setOpen(false)
                            onSelected(index)
                        },
                    )
                }
            }
        }
    }
}

/**
 * 图标触发钮 + 下方弹窗（顶栏更多菜单用）。
 */
@Composable
internal fun KazumiIconMenu(
    icon: @Composable () -> Unit,
    onOpenChange: (Boolean) -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.ColumnScope.(close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    fun setOpen(value: Boolean) {
        open = value
        onOpenChange(value)
    }
    // Popup 必须在锚点 Box 内部（同 KazumiTextMenu 注释）。
    Box {
        IconButton(
            onClick = { setOpen(true) },
            content = icon,
        )
        if (open) {
            KazumiMenuPopup(
                above = false,
                onDismiss = { setOpen(false) },
            ) {
                content { setOpen(false) }
            }
        }
    }
}

// ── Kazumi 式音量 pill ─────────────────────────────────────────────────
// 对应 Kazumi `PlayerAdjustmentHud`（volume 型）：200dp 宽胶囊（圆角 30dp），
// `surfaceContainerHighest` 底 + `outlineVariant` 34% 描边；内装 32dp primary
// 圆钮（20dp 音量图标，按音量分档）+ 横向滑块（激活 primary / 未激活
// secondaryContainer）。缺省动画用淡入淡出代替 Kazumi 的位移+缩放组合。
@Composable
internal fun BoxScope.VolumePill(
    visible: Boolean,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 76.dp),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Surface(
            modifier = Modifier.width(200.dp),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f),
            ),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val haptic = rememberHapticFeedback()
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            haptic()
                            onToggleMute()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(when {
                                volume <= 0f -> Res.drawable.ic_volume_off
                                volume < 0.45f -> Res.drawable.ic_volume_down
                                else -> Res.drawable.ic_volume_up
                            }),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = volume.coerceIn(0f, 1f),
                    onValueChange = onVolumeChange,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                )
            }
        }
    }
}
