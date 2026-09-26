package lovehan1me.video.contract

/**
 * 送给引擎的命令。命令是**意图**，不是状态 —— 命令能不能生效，看引擎随后发布的真值。
 */
sealed interface PlaybackCommand {

    /**
     * 打开一份媒体。
     *
     * @param isQualitySwitch 本次是切换画质（同一部片子的另一档）而非开新片。
     *   引擎据此保面：不重置首帧标记与画面尺寸，位置从 [startPositionMs] 续。
     */
    data class Load(val request: PlaybackRequest) : PlaybackCommand

    data object Play : PlaybackCommand

    data object Pause : PlaybackCommand

    data class SeekTo(val positionMs: Long) : PlaybackCommand

    data class SetSpeed(val speed: Float) : PlaybackCommand

    data class SetVolume(val volume: Float) : PlaybackCommand

    data class SetAspect(val mode: VideoAspectMode) : PlaybackCommand

    data class SetPicture(val adjust: PictureAdjust) : PlaybackCommand

    data object Release : PlaybackCommand
}

data class PlaybackRequest(
    val uri: String,
    val headers: Map<String, String> = emptyMap(),
    val title: String = "",
    val artworkUri: String? = null,
    val mimeType: String? = null,
    val startPositionMs: Long = 0L,
    val playWhenReady: Boolean = true,
    val isQualitySwitch: Boolean = false,
)

const val MIN_PLAYBACK_SPEED = 0.25f
const val MAX_PLAYBACK_SPEED = 5f

/** 倍速合法区间。三端唯一来源，UI 与引擎都过这一道。 */
fun Float.coercePlaybackSpeed(): Float = coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
