package lovehan1me.video.contract

enum class PlaybackPhase {
    Idle,
    Preparing,
    Ready,
    Ended,
    Error,
}

/**
 * 引擎**当前这一刻**读回来的真值（不含任何用户意图）。
 *
 * 契约层不认识 mediamp / ExoPlayer / AVPlayer 的任何类型，所以引擎负责把自己后端
 * 的状态折算成这个结构再交进来。可空/缺省的字段表示"这一刻读不到"，派生时会沿用
 * 上一次的真值，而不是拿 0 覆盖。
 */
data class PlaybackTruth(
    val phase: PlaybackPhase = PlaybackPhase.Idle,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    /** 引擎回报的**生效**倍速；null = 这一刻读不回来（沿用上一次真值）。 */
    val speed: Float? = null,
    /** 引擎回报的**生效**画面比例；null = 读不回来（沿用上一次真值）。 */
    val aspect: VideoAspectMode? = null,
    /** 引擎回报的**生效**画面调节；null = 读不回来（沿用上一次真值）。 */
    val picture: PictureAdjust? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val errorMessage: String? = null,
)

/**
 * 用户请求过的值（"意图"）。引擎每次开流都会丢掉画面类偏好，所以这些必须由调用方
 * 记住并在开流后重下 —— 状态里同时保留请求值与真值，UI 才不会显示一个
 * "选中了但其实没生效"的假状态。
 */
data class PlaybackRequests(
    val speed: Float = 1f,
    val aspect: VideoAspectMode = VideoAspectMode.Fit,
    val picture: PictureAdjust = PictureAdjust.Neutral,
)

/**
 * 开流这件事本身的状态。它是**意图**而不是真值，所以单独一列：
 * 引擎的 [PlaybackTruth] 只描述后端在干什么，描述不了"我们刚下发了一次开流、
 * 后端还没翻到 Opening"以及"开流同步失败了但后端状态还没跟上"这两种窗口。
 */
sealed interface PlaybackLoadStatus {
    data object Idle : PlaybackLoadStatus

    data class Opening(val isQualitySwitch: Boolean) : PlaybackLoadStatus

    data class Failed(val message: String) : PlaybackLoadStatus
}

/**
 * 播放会话的**唯一**状态快照。
 *
 * 请求值与真值分列同名不同字段（[requestedSpeed]/[actualSpeed]、
 * [requestedAspect]/[actualAspect]、[requestedPicture]/[actualPicture]）：
 * UI 要显示"选中了什么"用 requested，要显示"实际在发生什么"用 actual。
 *
 * 超分档位**不在这里**：它是独立的能力对象（[VideoEnhancementController]），
 * 自持生效档位，避免同一个状态两处所有权。
 *
 * 整个快照只由 [derivePlaybackState] 产出，任何地方都不得再逐字段手改。
 */
data class PlaybackState(
    val phase: PlaybackPhase = PlaybackPhase.Idle,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,

    val requestedSpeed: Float = 1f,
    val actualSpeed: Float = 1f,
    val requestedAspect: VideoAspectMode = VideoAspectMode.Fit,
    val actualAspect: VideoAspectMode = VideoAspectMode.Fit,
    val requestedPicture: PictureAdjust = PictureAdjust.Neutral,
    val actualPicture: PictureAdjust = PictureAdjust.Neutral,

    val videoWidth: Int = 0,
    val videoHeight: Int = 0,

    /** 首帧已渲染。单调锁存：只在开新片（非切画质）时清零，避免切档时海报闪回。 */
    val hasRenderedFirstFrame: Boolean = false,

    val errorMessage: String? = null,

    /** 正在切换画质（保面期间 UI 不盖转圈/海报）。到达 Ready 或 Error 自动撤销。 */
    val isSwitchingQuality: Boolean = false,
)
