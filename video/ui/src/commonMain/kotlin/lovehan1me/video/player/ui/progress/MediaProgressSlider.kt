/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.progress

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import lovehan1me.video.contract.safeCombine
import lovehan1me.video.player.ui.gesture.SwipeSeekerConfig
import lovehan1me.video.player.ui.gesture.isVerticalDragCancelled
import lovehan1me.video.player.ui.support.PlatformPopupProperties
import lovehan1me.video.player.ui.support.asGesturePointerType
import lovehan1me.video.player.ui.support.onPointerEventMultiplatform
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.chapters
import org.openani.mediamp.metadata.Chapter
import kotlin.math.roundToInt
import kotlin.math.roundToLong

const val TAG_PROGRESS_SLIDER_PREVIEW_POPUP = "ProgressSliderPreviewPopup"
const val TAG_PROGRESS_SLIDER_PREVIEW_FRAME = "ProgressSliderPreviewFrame"
const val TAG_PROGRESS_SLIDER_CENTERED_PREVIEW_FRAME = "ProgressSliderCenteredPreviewFrame"
const val TAG_PROGRESS_SLIDER = "ProgressSlider"

/**
 * 播放器进度滑块的状态.
 *
 * - 支持从 [currentPositionMillis] 同步当前播放位置, 从 [totalDurationMillis] 同步总时长.
 * - 使用 [onPreview] 和 [onPreviewFinished] 来处理用户拖动进度条的事件.
 *
 * @see MediaProgressSlider
 */
@Stable
class PlayerProgressSliderState(
    currentPositionMillis: () -> Long,
    totalDurationMillis: () -> Long,
    chapters: () -> List<Chapter>,
    /**
     * 当用户正在拖动进度条时触发. 每有一个 change 都会调用.
     */
    private val onPreview: (positionMillis: Long) -> Unit,
    /**
     * 当用户松开进度条时触发. 此时播放器应当要跳转到该位置.
     */
    private val onPreviewFinished: (positionMillis: Long) -> Unit,
) {
    val currentPositionMillis: Long by derivedStateOf(currentPositionMillis)
    val totalDurationMillis: Long by derivedStateOf(totalDurationMillis)
    val chapters by derivedStateOf(chapters)

    private var previewPositionRatio: Float by mutableFloatStateOf(Float.NaN)

    val isPreviewing: Boolean by derivedStateOf {
        !previewPositionRatio.isNaN()
    }

    /**
     * Sets the slider to move to the given position.
     * [onPreview] will be triggered.
     */
    fun previewPositionRatio(ratio: Float) {
        previewPositionRatio = ratio
        onPreview((totalDurationMillis * ratio).roundToLong())
    }

    /**
     * The ratio of the current display position to the total duration. Range is `0..1`
     */
    val displayPositionRatio by derivedStateOf {
        val previewPositionRatio = this.previewPositionRatio
        if (!previewPositionRatio.isNaN()) {
            return@derivedStateOf previewPositionRatio
        }

        val total = this.totalDurationMillis
        if (total == 0L) {
            return@derivedStateOf 0f
        }
        this.currentPositionMillis.toFloat() / total
    }

    fun finishPreview() {
        val ratio = this.previewPositionRatio
        if (ratio.isNaN()) return
        onPreviewFinished((ratio * totalDurationMillis).roundToLong())
        previewPositionRatio = Float.NaN
    }

    /**
     * Stops previewing without seeking to the previewed position.
     */
    fun cancelPreview() {
        previewPositionRatio = Float.NaN
    }
}

private class Data(
    val currentPosition: Long,
    val mediaProperties: org.openani.mediamp.metadata.MediaProperties?,
    val chapters: List<Chapter>,
) {
    @Stable
    companion object {
        @Stable
        val EMPTY = Data(0, null, emptyList())
    }
}

/**
 * 便捷方法, 从 [MediampPlayer.currentPositionMillis] 创建 [PlayerProgressSliderState]
 */
@Composable
fun rememberMediaProgressSliderState(
    player: MediampPlayer,
    chaptersFlow: Flow<List<Chapter>> = player.chapters ?: flowOf(emptyList()),
    onPreview: (positionMillis: Long) -> Unit,
    onPreviewFinished: (positionMillis: Long) -> Unit,
): PlayerProgressSliderState {
    val flow = remember(player, chaptersFlow) {
        safeCombine(
            player.currentPositionMillis,
            player.mediaProperties,
            chaptersFlow,
            ::Data,
        )
    }

    val data by flow.collectAsStateWithLifecycle(Data.EMPTY)

    val totalDuration by remember {
        derivedStateOf {
            data.mediaProperties?.durationMillis ?: 0L
        }
    }

    val onPreviewUpdated by rememberUpdatedState(onPreview)
    val onPreviewFinishedUpdated by rememberUpdatedState(onPreviewFinished)
    return remember {
        PlayerProgressSliderState(
            { data.currentPosition },
            { totalDuration },
            { data.chapters },
            onPreviewUpdated,
            onPreviewFinishedUpdated,
        )
    }
}

object MediaProgressSliderDefaults {
    @Composable
    fun colors(
        trackBackgroundColor: Color = MaterialTheme.colorScheme.surface,
        trackProgressColor: Color = MaterialTheme.colorScheme.primary,
        thumbColor: Color = MaterialTheme.colorScheme.primary,
        chapterColor: Color = MaterialTheme.colorScheme.onSurface,
        previewTimeBackgroundColor: Color = MaterialTheme.colorScheme.surface,
        previewTimeTextColor: Color = MaterialTheme.colorScheme.onSurface,
    ): MediaProgressSliderColors {
        return MediaProgressSliderColors(
            trackBackgroundColor,
            trackProgressColor,
            thumbColor,
            chapterColor,
            previewTimeBackgroundColor,
            previewTimeTextColor,
        )
    }
}

@Immutable
class MediaProgressSliderColors(
    val trackBackgroundColor: Color,
    val trackProgressColor: Color,
    val thumbColor: Color,
    val chapterColor: Color,
    val previewTimeBackgroundColor: Color,
    val previewTimeTextColor: Color,
)

/**
 * 直接拖动进度条时的触摸手势状态机, 不参与鼠标交互.
 *
 * 状态只按以下路径迁移:
 * ```
 * Idle --start--> Seeking
 * Seeking --move upward past threshold--> Cancelling
 * Cancelling --move back within threshold--> Seeking
 * Seeking / Cancelling --stop--> Idle
 * ```
 * [move] 根据手指相对按下点的上滑距离，在 [State.Seeking] 和 [State.Cancelling] 之间切换；
 * [stop] 返回松手时是否处于取消状态，供进度条决定提交或放弃 seek.
 * [onStateChanged] 只在状态实际变化时调用，控制器显隐和取消提示统一在这里响应.
 */
@Stable
class TouchSeekState(
    swipeSeekerConfig: SwipeSeekerConfig,
    density: Density,
    val onStateChanged: (State) -> Unit,
) {
    private val cancelVerticalDragDistancePx =
        with(density) { swipeSeekerConfig.cancelVerticalDragDistance.toPx() }

    enum class State {
        Idle,
        Seeking,
        Cancelling,
    }

    var state: State = State.Idle
        private set

    private var dragStartY: Float = Float.NaN

    internal fun onPointerDown(position: Offset) {
        if (state == State.Idle && position.isSpecified) {
            dragStartY = position.y
        }
    }

    internal fun start() {
        transitionTo(State.Seeking)
    }

    internal fun move(position: Offset): Boolean {
        val cancelling = isVerticalDragCancelled(
            dragStartY,
            position,
            cancelVerticalDragDistancePx,
        )
        return transitionTo(if (cancelling) State.Cancelling else State.Seeking)
    }

    internal fun stop(): Boolean {
        val cancelled = state == State.Cancelling
        transitionTo(State.Idle)
        dragStartY = Float.NaN
        return cancelled
    }

    private fun transitionTo(newState: State): Boolean {
        if (state == newState) return false
        state = newState
        onStateChanged(newState)
        return true
    }
}

/**
 * 视频播放器的进度条, 支持拖动调整播放位置, 支持显示章节标记.
 */
@Composable
fun MediaProgressSlider(
    state: PlayerProgressSliderState,
    colors: MediaProgressSliderColors = MediaProgressSliderDefaults.colors(),
    enabled: Boolean = true,
    showPreviewTimeTextOnThumb: Boolean = true,
    framePreview: MediaProgressFramePreviewState? = null,
    showFramePreviewInPopup: Boolean = true,
    touchSeekState: TouchSeekState? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxWidth()
            .height(24.dp)
            .testTag(TAG_PROGRESS_SLIDER),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.fillMaxWidth().height(6.dp)
                .padding(horizontal = 2.dp) // half thumb width
                .clip(CircleShape),
        ) {
            Canvas(Modifier.matchParentSize()) {
                // draw track
                drawRect(
                    colors.trackBackgroundColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, size.height),
                )
            }

            Canvas(Modifier.matchParentSize()) {
                // draw play progress
                val xPlay = size.width * state.displayPositionRatio

                drawRect(
                    colors.trackProgressColor,
                    topLeft = Offset(0f, 0f),
                    size = Size(xPlay, size.height),
                )
            }

            Canvas(Modifier.matchParentSize()) {
                if (state.totalDurationMillis == 0L) return@Canvas
                state.chapters.forEach { chapter ->
                    fun drawChapterMarker(millis: Long) {
                        val percent = millis.toFloat().div(state.totalDurationMillis)
                        drawCircle(
                            color = colors.chapterColor,
                            radius = 2.dp.toPx(),
                            center = Offset(size.width * percent, this.center.y),
                            blendMode = BlendMode.Src, // override background
                        )
                    }
                    drawChapterMarker(chapter.offsetMillis)

                    // also draw end marker
                    val endMillis = chapter.offsetMillis + chapter.durationMillis
                    if (state.chapters.none { it.offsetMillis == endMillis }) {
                        drawChapterMarker(endMillis)
                    }
                }
            }
        }

        var mousePosX by rememberSaveable { mutableStateOf(0f) }
        var thumbWidth by rememberSaveable { mutableIntStateOf(0) }
        var sliderWidth by rememberSaveable { mutableIntStateOf(0) }
        var latestTouchPreviewRatio by remember { mutableFloatStateOf(Float.NaN) }
        var handlingTouchInput by remember { mutableStateOf(false) }

        fun renderPreviewTime(previewTimeMillis: Long): String {
            state.chapters.find {
                previewTimeMillis in it.offsetMillis..<it.offsetMillis + it.durationMillis
            }?.let {
                val chapterName = if (it.name.isBlank()) "" else it.name + "\n"
                return chapterName + renderSeconds(
                    previewTimeMillis / 1000,
                    state.totalDurationMillis / 1000,
                ).substringBefore(" ")
            }

            return renderSeconds(previewTimeMillis / 1000, state.totalDurationMillis / 1000).substringBefore(" ")
        }

        val previewTimeText by remember {
            derivedStateOf {
                val containerWidth = sliderWidth - thumbWidth
                if (containerWidth == 0) { // avoid division by zero during preview or in a extremely small container
                    ""
                } else {
                    val percent = mousePosX.minus(thumbWidth / 2).div(containerWidth)
                        .coerceIn(0f, 1f)
                    val previewTimeMillis = state.totalDurationMillis.times(percent).toLong()

                    renderPreviewTime(previewTimeMillis)
                }
            }
        }
        val previewTimeOnThumb by remember(state) {
            derivedStateOf {
                val previewTimeMillis = state.totalDurationMillis.times(state.displayPositionRatio).toLong()

                renderPreviewTime(previewTimeMillis)
            }
        }
        val hoverInteraction = remember { MutableInteractionSource() }
        val isHovered by hoverInteraction.collectIsHoveredAsState() // works only for desktop
        var isPressed by remember { mutableStateOf(false) }
        val showPreviewTime by remember {
            derivedStateOf {
                isHovered || isPressed
            }
        }
        if (framePreview != null) {
            // 悬浮或拖动时加载目标位置的预览帧, 显示在浮窗中.
            val previewingPositionMillis by remember(state) {
                derivedStateOf {
                    when {
                        state.isPreviewing && showPreviewTimeTextOnThumb ->
                            (state.totalDurationMillis * state.displayPositionRatio).toLong()

                        showPreviewTime -> {
                            val containerWidth = sliderWidth - thumbWidth
                            if (containerWidth <= 0) {
                                null
                            } else {
                                val percent = mousePosX.minus(thumbWidth / 2).div(containerWidth)
                                    .coerceIn(0f, 1f)
                                (state.totalDurationMillis * percent).toLong()
                            }
                        }

                        else -> null
                    }
                }
            }
            LaunchedEffect(framePreview, state) {
                snapshotFlow { previewingPositionMillis }
                    .collectLatest { positionMillis ->
                        if (positionMillis == null) {
                            framePreview.onPreviewFinished()
                            return@collectLatest
                        }
                        val total = state.totalDurationMillis
                        if (total <= 0) return@collectLatest
                        framePreview.requestFrame(positionMillis)
                    }
            }
        }
        if (showPreviewTime) {
            val showFrame = showFramePreviewInPopup && framePreview != null
            ProgressSliderPreviewPopup(
                offsetX = { mousePosX.roundToInt() },
                previewTimeBackgroundColor = colors.previewTimeBackgroundColor,
                shape = previewPopupShape(showFrame),
            ) {
                ProgressSliderPreviewContent(
                    frame = framePreview?.frame,
                    text = previewTimeText,
                    previewTimeTextColor = colors.previewTimeTextColor,
                    showFrame = showFrame,
                )
            }
        }
        // draw thumb
        val interactionSource = remember { MutableInteractionSource() }
        Slider(
            value = state.displayPositionRatio,
            valueRange = 0f..1f,
            onValueChange = {
                if (handlingTouchInput && touchSeekState?.state == TouchSeekState.State.Idle) {
                    touchSeekState.start()
                }
                latestTouchPreviewRatio = it
                if (touchSeekState?.state != TouchSeekState.State.Cancelling) {
                    state.previewPositionRatio(it)
                }
            },
            interactionSource = interactionSource,
            thumb = {
                Canvas(Modifier.width(12.dp).height(24.dp)) {
                    drawCircle(
                        colors.thumbColor,
                        radius = 8.dp.toPx(),
                    )
                }

                // 仅在 detached slider 上显示
                if (state.isPreviewing && showPreviewTimeTextOnThumb) {
                    val showFrame = showFramePreviewInPopup && framePreview != null
                    ProgressSliderPreviewPopup(
                        offsetX = { thumbWidth / 2 },
                        previewTimeBackgroundColor = colors.previewTimeBackgroundColor,
                        shape = previewPopupShape(showFrame),
                    ) {
                        ProgressSliderPreviewContent(
                            frame = framePreview?.frame,
                            text = previewTimeOnThumb,
                            previewTimeTextColor = colors.previewTimeTextColor,
                            showFrame = showFrame,
                        )
                    }
                }
            },
            track = {
                SliderDefaults.Track(
                    it,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                        disabledActiveTrackColor = Color.Transparent,
                        disabledInactiveTrackColor = Color.Transparent,
                    ),
                )
            },
            onValueChangeFinished = {
                val cancelled = handlingTouchInput && touchSeekState?.stop() == true
                handlingTouchInput = false
                if (cancelled) {
                    state.cancelPreview()
                } else {
                    state.finishPreview()
                }
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(24.dp)
                .onSizeChanged {
                    sliderWidth = it.width
                }
                .hoverable(interactionSource = hoverInteraction)
                .onPointerEventMultiplatform(
                    PointerEventType.Press,
                    pass = PointerEventPass.Initial,
                ) { event ->
                    val touchChange = event.changes.firstOrNull()
                        ?.takeIf { it.type.asGesturePointerType() == PointerType.Touch }
                    handlingTouchInput = touchChange != null
                    touchChange?.let { touchSeekState?.onPointerDown(it.position) }
                }
                .onPointerEventMultiplatform(
                    PointerEventType.Move,
                    pass = PointerEventPass.Initial,
                ) { event ->
                    val change = event.changes.firstOrNull() ?: return@onPointerEventMultiplatform
                    mousePosX = change.position.x

                    val touchSeekState = touchSeekState ?: return@onPointerEventMultiplatform
                    if (!handlingTouchInput || touchSeekState.state == TouchSeekState.State.Idle) {
                        return@onPointerEventMultiplatform
                    }
                    if (!touchSeekState.move(change.position)) {
                        return@onPointerEventMultiplatform
                    }

                    if (touchSeekState.state == TouchSeekState.State.Cancelling) {
                        state.cancelPreview()
                    } else if (!latestTouchPreviewRatio.isNaN()) {
                        state.previewPositionRatio(latestTouchPreviewRatio)
                    }
                },
        )
    }
}

@Composable
private fun ProgressSliderPreviewContent(
    frame: ImageBitmap?,
    text: String,
    previewTimeTextColor: Color,
    showFrame: Boolean,
) {
    if (showFrame) {
        PreviewFrameAndTimeText(
            frame = frame,
            text = text,
            previewTimeTextColor = previewTimeTextColor,
            showFrameArea = true,
        )
    } else {
        PreviewTimeText(text, previewTimeTextColor)
    }
}

/**
 * 浮窗形状: 只有时间文字时用胶囊形; 有预览帧时用圆角矩形, 避免图片角被大圆角裁掉.
 */
@Composable
internal fun previewPopupShape(hasFrame: Boolean): Shape =
    if (hasFrame) RoundedCornerShape(12.dp) else CircleShape

@Composable
fun ProgressSliderPreviewPopup(
    offsetX: () -> Int,
    previewTimeBackgroundColor: Color,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val popupPositionProviderState = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val anchor = IntRect(
                    offset = IntOffset(
                        offsetX(),
                        with(density) { -8.dp.toPx().toInt() },
                    ) + anchorBounds.topLeft,
                    size = IntSize.Zero,
                )
                val tooltipArea = IntRect(
                    IntOffset(
                        anchor.left - popupContentSize.width,
                        anchor.top - popupContentSize.height,
                    ),
                    IntSize(
                        popupContentSize.width * 2,
                        popupContentSize.height * 2,
                    ),
                )
                val position = Alignment.TopCenter.align(popupContentSize, tooltipArea.size, layoutDirection)

                return IntOffset(
                    x = (tooltipArea.left + position.x).coerceIn(0, windowSize.width - popupContentSize.width),
                    y = (tooltipArea.top + position.y).coerceIn(0, windowSize.height - popupContentSize.height),
                )
            }
        }
    }
    Popup(
        properties = PlatformPopupProperties(usePlatformInsets = false),
        popupPositionProvider = popupPositionProviderState,
    ) {
        Box(
            modifier = modifier
                .testTag(TAG_PROGRESS_SLIDER_PREVIEW_POPUP)
                .clip(shape = shape)
                .background(previewTimeBackgroundColor)
                .animateContentSize(),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

/**
 * 浮窗内容: 启用预览帧时, 在时间上方显示固定尺寸的帧图区域 (帧未加载时显示占位背景,
 * 保证浮窗大小从出现起就固定, 不随帧的加载而跳动); 未启用时只显示时间.
 */
@Composable
fun PreviewFrameAndTimeText(
    frame: ImageBitmap?,
    text: String,
    previewTimeTextColor: Color,
    showFrameArea: Boolean = frame != null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (showFrameArea) {
            Box(
                Modifier
                    .padding(bottom = 8.dp)
                    .size(width = 160.dp, height = 90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                if (frame != null) {
                    Image(
                        frame,
                        contentDescription = null,
                        Modifier
                            .matchParentSize()
                            .testTag(TAG_PROGRESS_SLIDER_PREVIEW_FRAME),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        PreviewTimeText(text, previewTimeTextColor)
    }
}

/**
 * Compact 播放器布局中显示在播放器中央的预览帧.
 */
@Composable
fun ProgressSliderCenteredPreviewFrame(
    frame: ImageBitmap?,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    if (frame == null) return

    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .size(width = 160.dp, height = 90.dp)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.3f))
            .border(2.dp, borderColor, shape)
            .testTag(TAG_PROGRESS_SLIDER_CENTERED_PREVIEW_FRAME),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            frame,
            contentDescription = null,
            Modifier.matchParentSize(),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
fun PreviewTimeText(
    text: String,
    previewTimeTextColor: Color,
) {
    Box(contentAlignment = Alignment.Center) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            Text(
                // 占位置
                text = text,
                Modifier.alpha(0f),
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = text,
                color = previewTimeTextColor,
                textAlign = TextAlign.Center,
            )
        }
    }
}
