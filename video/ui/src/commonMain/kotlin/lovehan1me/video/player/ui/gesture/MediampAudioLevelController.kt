/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.gesture

import org.openani.mediamp.features.AudioLevelController
import org.openani.mediamp.features.toggleMute

/**
 * mediamp 音量 feature 的 [LevelController] 包装。
 *
 * 直接透传的话调用方拿不到"用户把音量改成了多少"这件事 —— [onVolumeStateChanged]
 * 让宿主在这一次变更落盘（含静音，静音不改 volume 值所以要单独报）。
 */
class MediampAudioLevelController(
    private val controller: AudioLevelController,
    private val onVolumeStateChanged: (level: Float, mute: Boolean) -> Unit,
) : LevelController {
    override val level: Float get() = controller.volume.value

    val levelFlow = controller.volume

    val muteFlow = controller.isMute

    override val range: ClosedRange<Float> = 0f..controller.maxVolume

    override fun setLevel(level: Float) {
        val newLevel = level.coerceIn(range)
        controller.setVolume(newLevel)
        onVolumeStateChanged(newLevel, controller.isMute.value)
    }

    fun toggleMute() {
        val targetIsMute = !muteFlow.value
        controller.toggleMute()
        onVolumeStateChanged(level, targetIsMute)
    }
}
