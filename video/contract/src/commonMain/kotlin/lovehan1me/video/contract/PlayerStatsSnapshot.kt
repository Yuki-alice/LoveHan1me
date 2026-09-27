/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.contract

/**
 * 某一时刻的播放诊断快照。
 *
 * 定义在契约层：只有引擎能读到后端私有信息（mpv 属性、Exo 统计），而只有 UI 渲染它，
 * 两者互不依赖，只能在这里会合。取不到的字段一律为 null，UI 负责整行省略。
 */
data class PlayerStatsSnapshot(
    val backend: String,
    val playbackState: String,
    val title: String?,
    val positionMillis: Long,
    val durationMillis: Long?,
    val playbackSpeed: Float?,
    val resolution: String?,
    val frameRate: Float?,
    val videoCodec: String?,
    val videoBitrate: Long?,
    val audioCodec: String?,
    val audioBitrate: Long?,
    val audioSampleRate: Int?,
    val audioChannels: Int?,
    val realtimeInputBitrate: Long?,
    val realtimeDemuxBitrate: Long?,
    val decodedVideoFrames: Long?,
    val decodedAudioFrames: Long?,
    val droppedVideoFrames: Long?,
    val droppedAudioBuffers: Long?,
)
