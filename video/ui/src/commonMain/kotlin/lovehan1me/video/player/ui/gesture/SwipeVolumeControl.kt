/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.gesture

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import lovehan1me.video.contract.AudioManager
import lovehan1me.video.contract.BrightnessManager
import lovehan1me.video.contract.StreamType

/**
 * 音量与亮度在手势层是同一种东西：一个 0..1 的、可分档调节的级别。
 * 统一成这一个接口，左右半屏拖动就能共用同一套 SteppedDraggable 与指示器。
 */
interface LevelController {
    val level: Float

    val range: ClosedRange<Float>

    /** 该平台能表示的最小变化量。 */
    val levelStep: Float get() = 0.01f

    fun setLevel(level: Float)
}

object NoOpLevelController : LevelController {
    override val level: Float
        get() = 0f

    override val range: ClosedRange<Float> = 0f..1f

    override fun setLevel(level: Float) {
    }
}

/**
 * 级别由宿主自己持有时的适配。
 *
 * 用在后端没有音量回报通道的端（Exo / AVKit 只有写入没有读取）：写入照常下发，
 * 读取宿主维护的那份状态。**levelState 必须是 MutableState 而不是普通值** ——
 * 控制器身份要稳定（修饰符按它挂载），而 level 每次读都得是当下最新。
 */
@Stable
class StateLevelController(
    private val levelState: State<Float>,
    override val range: ClosedRange<Float> = 0f..1f,
    private val onSetLevel: (Float) -> Unit,
) : LevelController {
    override val level: Float get() = levelState.value

    override fun setLevel(level: Float) = onSetLevel(level.coerceIn(range))
}

fun LevelController.increaseLevel(step: Float = 0.05f) {
    setLevel((level + step).coerceAtMost(range.endInclusive))
}

fun LevelController.decreaseLevel(step: Float = 0.05f) {
    setLevel((level - step).coerceAtLeast(range.start))
}

fun AudioManager.asLevelController(
    streamType: StreamType,
): LevelController = object : LevelController {
    override val level: Float
        get() = getVolume(streamType)

    override val range: ClosedRange<Float> = 0f..1f

    override val levelStep: Float
        get() = getVolumeStep(streamType)

    override fun setLevel(level: Float) {
        setVolume(streamType, level.coerceIn(range))
    }
}

fun BrightnessManager.asLevelController(): LevelController = object : LevelController {
    override val level: Float
        get() = getBrightness()

    override val range: ClosedRange<Float> = 0f..1f

    override fun setLevel(level: Float) {
        setBrightness(level.coerceIn(range))
    }
}

fun Modifier.swipeLevelControlWithIndicator(
    controller: LevelController,
    stepSize: Dp,
    orientation: Orientation,
    indicatorState: GestureIndicatorState,
    enabled: Boolean = true,
    step: Float = 0.05f,
    setup: () -> Unit = {},
): Modifier = this then swipeLevelControl(
    controller = controller,
    stepSize = stepSize,
    orientation = orientation,
    step = step,
    enabled = enabled,
    afterStep = {
        setup()
        indicatorState.progressValue = controller.level
    },
    onDragStarted = {
        indicatorState.visible = true
    },
    onDragStopped = {
        indicatorState.visible = false
    },
)

fun Modifier.swipeLevelControl(
    controller: LevelController,
    stepSize: Dp,
    orientation: Orientation,
    step: Float = 0.05f,
    enabled: Boolean = true,
    afterStep: (StepDirection) -> Unit = {},
    onDragStarted: suspend CoroutineScope.(startedPosition: Offset) -> Unit = {},
    onDragStopped: suspend CoroutineScope.(velocity: Float) -> Unit = {},
): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "swipeLevelControl"
        properties["controller"] = controller
        properties["stepSize"] = stepSize
        properties["orientation"] = orientation
    },
) {
    steppedDraggable(
        rememberSteppedDraggableState(
            stepSize = stepSize,
            onStep = { direction ->
                when (direction) {
                    StepDirection.FORWARD -> controller.increaseLevel(step)
                    StepDirection.BACKWARD -> controller.decreaseLevel(step)
                }
                afterStep(direction)
            },
        ),
        orientation = orientation,
        enabled = enabled,
        onDragStarted = onDragStarted,
        onDragStopped = onDragStopped,
    )
}
