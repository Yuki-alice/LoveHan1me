/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import lovehan1me.video.contract.VideoAspectMode
import lovehan1me.video.ui.Res
import lovehan1me.video.ui.player_aspect_crop
import lovehan1me.video.ui.player_aspect_fit
import lovehan1me.video.ui.player_aspect_stretch
import org.jetbrains.compose.resources.stringResource
import org.openani.mediamp.InternalForInheritanceMediampApi
import org.openani.mediamp.features.AspectRatioMode
import org.openani.mediamp.features.VideoAspectRatio

/**
 * 画面比例的**生效**状态：直接读 mediamp 回报的 mode，不记意图。
 * 引擎每次开流都会丢掉画面偏好，重下由调用方负责（见 PlaybackRequests）。
 *
 * @param onCommitMode 选中一档后回调。feature 本身不记"用户想要什么"，
 *   换画质/重播后要把这一档重下的责任在调用方。
 */
@Stable
class VideoAspectRatioControllerState(
    private val videoAspectRatio: VideoAspectRatio,
    scope: CoroutineScope,
    private val onCommitMode: (AspectRatioMode) -> Unit = {},
) {
    var currentMode by mutableStateOf(videoAspectRatio.mode.value)
    val currentIndex by derivedStateOf { Entries.indexOf(currentMode) }

    init {
        scope.launch {
            videoAspectRatio.mode.collect {
                currentMode = it
            }
        }
    }

    fun setMode(mode: AspectRatioMode) {
        videoAspectRatio.setMode(mode)
        onCommitMode(mode)
    }

    companion object {
        val Entries: List<AspectRatioMode> = AspectRatioMode.entries
    }
}

@Composable
fun renderAspectRatioMode(mode: AspectRatioMode): String {
    return when (mode) {
        AspectRatioMode.FIT -> stringResource(Res.string.player_aspect_fit)
        AspectRatioMode.STRETCH -> stringResource(Res.string.player_aspect_stretch)
        AspectRatioMode.CROP -> stringResource(Res.string.player_aspect_crop)
    }
}

/**
 * mediamp 档位 → 契约档位。
 *
 * 控件写 feature 只能改"这一刻的画面"，而引擎在每次开流后会重下**它自己记住的请求**；
 * 要把选择留住就得同时调 `PlaybackController.setVideoAspect`，那需要契约侧的枚举。
 */
@Stable
fun AspectRatioMode.toContractMode(): VideoAspectMode = when (this) {
    AspectRatioMode.FIT -> VideoAspectMode.Fit
    AspectRatioMode.STRETCH -> VideoAspectMode.Stretch
    AspectRatioMode.CROP -> VideoAspectMode.Crop
}

@OptIn(InternalForInheritanceMediampApi::class)
object NoOpVideoAspectRatio : VideoAspectRatio {
    override val mode: StateFlow<AspectRatioMode> = MutableStateFlow(AspectRatioMode.FIT)
    override fun setMode(mode: AspectRatioMode) {

    }
}
