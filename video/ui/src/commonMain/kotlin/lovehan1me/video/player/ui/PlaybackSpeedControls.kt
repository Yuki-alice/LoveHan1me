/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import lovehan1me.video.player.ui.support.SLIDER_VALUE_STEP
import lovehan1me.video.player.ui.support.quantizeSliderValue
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.features.PlaybackSpeed

private const val KEYBOARD_SPEED_STEP: Float = SLIDER_VALUE_STEP

/** 将键盘输入转换为提交给调用方的最终倍速。 */
fun nextPlaybackSpeed(
    currentSpeed: Float,
    range: ClosedFloatingPointRange<Float>,
    direction: Int,
): Float = quantizeSliderValue(currentSpeed + direction * KEYBOARD_SPEED_STEP, range)

/**
 * 倍速控制（SpeedSwitcher、键盘快捷键）的 state object。
 *
 * Side-effect: 创建本状态会立即把 [PlaybackSpeed.valueFlow] 收集进 [currentSpeed]。
 *
 * @param rangeProvider 用户配置的倍速范围，每次读取 [speedRange] 时重新求值。
 * @param onCommitSpeed 倍速被提交（松手或键盘调整）后的回调，通常用于持久化全局倍速。
 * @param scope 倍速收集器用的 coroutine scope，通常是 `rememberCoroutineScope`。
 */
@Stable
class PlaybackSpeedControllerState(
    private val playbackSpeed: PlaybackSpeed,
    rangeProvider: () -> ClosedFloatingPointRange<Float> = { DEFAULT_SPEED_RANGE },
    private val onCommitSpeed: (Float) -> Unit = {},
    scope: CoroutineScope,
) {
    val speedRange: ClosedFloatingPointRange<Float> by derivedStateOf(rangeProvider)

    var currentSpeed: Float by mutableStateOf(playbackSpeed.value)
        private set

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            playbackSpeed.valueFlow
                .distinctUntilChanged()
                .collect { value -> currentSpeed = value }
        }
    }

    /**
     * 拖动 Slider 期间实时预览倍速，不触发 [onCommitSpeed]。
     */
    fun previewSpeed(value: Float) {
        applySpeed(value)
    }

    /**
     * 提交最终倍速：应用到播放器并回调 [onCommitSpeed]。
     */
    fun commitSpeed(value: Float) {
        applySpeed(value)
        onCommitSpeed(value)
    }

    private fun applySpeed(value: Float) {
        currentSpeed = value
        playbackSpeed.set(value)
    }

    companion object {
        val DEFAULT_SPEED_RANGE: ClosedFloatingPointRange<Float> = 0.5f..2.5f
    }
}

@OptIn(InternalForInheritanceMediampApi::class)
object NoOpPlaybackSpeedController : PlaybackSpeed {
    override val value: Float = 1f
    override val valueFlow: Flow<Float> = flowOf(1f)

    override fun set(speed: Float) {
    }
}
