/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import lovehan1me.video.contract.PlayerStatsSnapshot
import lovehan1me.video.ui.Res
import lovehan1me.video.ui.player_stats_audio_bitrate
import lovehan1me.video.ui.player_stats_audio_codec
import lovehan1me.video.ui.player_stats_audio_format
import lovehan1me.video.ui.player_stats_backend
import lovehan1me.video.ui.player_stats_decode_stats
import lovehan1me.video.ui.player_stats_dropped_audio_buffers
import lovehan1me.video.ui.player_stats_dropped_video_frames
import lovehan1me.video.ui.player_stats_frame_rate
import lovehan1me.video.ui.player_stats_hide_hint
import lovehan1me.video.ui.player_stats_media_title
import lovehan1me.video.ui.player_stats_playback_speed
import lovehan1me.video.ui.player_stats_progress
import lovehan1me.video.ui.player_stats_realtime_demux
import lovehan1me.video.ui.player_stats_realtime_input
import lovehan1me.video.ui.player_stats_resolution
import lovehan1me.video.ui.player_stats_state
import lovehan1me.video.ui.player_stats_title
import lovehan1me.video.ui.player_stats_video_bitrate
import lovehan1me.video.ui.player_stats_video_codec
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * 播放信息叠层。取不到的字段整行不显示。
 */
@Composable
fun PlayerStatsOverlay(
    stats: PlayerStatsSnapshot?,
    modifier: Modifier = Modifier,
) {
    if (stats == null) return

    Surface(
        modifier,
        color = Color.Black.copy(alpha = 0.72f),
        contentColor = Color.White,
        // 10dp 就近归档 medium（12dp），收敛到形状 token。
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 4.dp,
    ) {
        Column(
            Modifier
                .background(Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "${stringResource(Res.string.player_stats_title)}  ${stringResource(Res.string.player_stats_hide_hint)}",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                ),
                color = Color.White,
            )
            PlayerStatsRow(stringResource(Res.string.player_stats_backend), stats.backend)
            PlayerStatsRow(stringResource(Res.string.player_stats_state), stats.playbackState)
            stats.title?.takeIf { it.isNotBlank() }
                ?.let { PlayerStatsRow(stringResource(Res.string.player_stats_media_title), it) }
            PlayerStatsRow(
                stringResource(Res.string.player_stats_progress),
                "${formatDuration(stats.positionMillis)} / ${formatDuration(stats.durationMillis)}",
            )
            stats.resolution?.let { PlayerStatsRow(stringResource(Res.string.player_stats_resolution), it) }
            stats.frameRate?.let {
                PlayerStatsRow(stringResource(Res.string.player_stats_frame_rate), "${formatDecimal(it)} fps")
            }
            stats.videoCodec?.let { PlayerStatsRow(stringResource(Res.string.player_stats_video_codec), it) }
            formatBitrate(stats.videoBitrate)?.let {
                PlayerStatsRow(stringResource(Res.string.player_stats_video_bitrate), it)
            }
            stats.audioCodec?.let { PlayerStatsRow(stringResource(Res.string.player_stats_audio_codec), it) }
            formatBitrate(stats.audioBitrate)?.let {
                PlayerStatsRow(stringResource(Res.string.player_stats_audio_bitrate), it)
            }
            listOfNotNull(
                stats.audioSampleRate?.takeIf { it > 0 }?.let { "${it} Hz" },
                stats.audioChannels?.takeIf { it > 0 }?.let { "${it} ch" },
            ).joinToString(" / ").takeIf { it.isNotBlank() }?.let {
                PlayerStatsRow(stringResource(Res.string.player_stats_audio_format), it)
            }
            stats.playbackSpeed?.let {
                PlayerStatsRow(stringResource(Res.string.player_stats_playback_speed), "${formatDecimal(it)}x")
            }
            formatBitrate(stats.realtimeInputBitrate)?.let {
                PlayerStatsRow(stringResource(Res.string.player_stats_realtime_input), it)
            }
            formatBitrate(stats.realtimeDemuxBitrate)?.let {
                PlayerStatsRow(stringResource(Res.string.player_stats_realtime_demux), it)
            }
            listOfNotNull(
                stats.decodedVideoFrames?.let { "V $it" },
                stats.decodedAudioFrames?.let { "A $it" },
                stats.droppedVideoFrames?.takeIf { it > 0 }
                    ?.let { stringResource(Res.string.player_stats_dropped_video_frames, it.toString()) },
                stats.droppedAudioBuffers?.takeIf { it > 0 }
                    ?.let { stringResource(Res.string.player_stats_dropped_audio_buffers, it.toString()) },
            ).joinToString(" / ").takeIf { it.isNotBlank() }?.let {
                PlayerStatsRow(stringResource(Res.string.player_stats_decode_stats), it)
            }
        }
    }
}

@Composable
private fun PlayerStatsRow(
    label: String,
    value: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Color.White.copy(alpha = 0.72f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Color.White,
        )
    }
}

private fun formatDuration(millis: Long?): String {
    if (millis == null || millis < 0) return "--:--"
    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "${hours}:${minutes.toPadded2()}:${seconds.toPadded2()}"
    } else {
        "${minutes}:${seconds.toPadded2()}"
    }
}

private fun Long.toPadded2(): String = if (this < 10) "0$this" else toString()

private fun formatBitrate(bitsPerSecond: Long?): String? {
    if (bitsPerSecond == null || bitsPerSecond <= 0) return null
    return if (bitsPerSecond >= 1_000_000) {
        "${formatDecimal(bitsPerSecond / 1_000_000f)} Mbps"
    } else {
        "${(bitsPerSecond / 1000f).roundToInt()} kbps"
    }
}

private fun formatDecimal(value: Float): String {
    val rounded = (value * 100).roundToInt() / 100f
    val text = rounded.toString()
    return if (text.endsWith(".0")) text.dropLast(2) else text
}
