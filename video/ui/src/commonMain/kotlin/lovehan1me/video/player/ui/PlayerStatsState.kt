/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import lovehan1me.video.contract.PlayerStatsSnapshot
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.PlaybackSpeed
import kotlin.time.Duration.Companion.seconds

/**
 * 每秒采一次播放信息。
 *
 * 只读 mediamp 公开 API，因此三端同一份实现；内核独有的细节（解码器、码率、
 * 丢帧、硬解方式）要由后端句柄补充，那种读取只能住在 `:video:engine`。
 */
@Composable
fun rememberPlayerStatsState(player: MediampPlayer): State<PlayerStatsSnapshot?> {
    return produceState<PlayerStatsSnapshot?>(initialValue = null, player) {
        while (true) {
            value = player.readPlayerStats()
            delay(1.seconds)
        }
    }
}

private fun MediampPlayer.readPlayerStats(): PlayerStatsSnapshot {
    val properties = mediaProperties.value
    val width = properties?.videoWidth
    val height = properties?.videoHeight
    return PlayerStatsSnapshot(
        backend = impl::class.simpleName ?: "mediamp",
        playbackState = state.value.toString(),
        title = properties?.title,
        positionMillis = currentPositionMillis.value,
        durationMillis = properties?.durationMillis,
        playbackSpeed = features[PlaybackSpeed]?.value,
        resolution = if (width != null && height != null) "${width}×${height}" else null,
        frameRate = null,
        videoCodec = null,
        videoBitrate = null,
        audioCodec = null,
        audioBitrate = null,
        audioSampleRate = null,
        audioChannels = null,
        realtimeInputBitrate = null,
        realtimeDemuxBitrate = null,
        decodedVideoFrames = null,
        decodedAudioFrames = null,
        droppedVideoFrames = null,
        droppedAudioBuffers = null,
    )
}
