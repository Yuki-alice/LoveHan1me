package lovehan1me.video.contract

const val DEFAULT_ERROR_MESSAGE = "playback error"

/**
 * 播放状态的**唯一**写者：把「上一帧状态 + 后端真值 + 用户请求值 + 开流意图」
 * 折算成一个新快照。
 *
 * 为什么必须是纯函数：此前 `load()` 直接往状态里写 `phase=Preparing` / `phase=Error`，
 * 而状态收集循环下一次 `publish()` 又按后端快照无条件覆盖 —— 开流失败写的 Error
 * 会在下一帧被抹掉，UI 永远停在转圈。把"意图"也做成入参后，同一个意图在每个
 * publish 里都会得到同一个结果，抹不掉。
 *
 * 不变式（改动前先想清楚）：
 * - 只有本函数能产出 [PlaybackState]；引擎不得再逐字段 `copy`。
 * - 读不回来的真值一律沿用上一次，**不得拿 0 / 默认值覆盖**。
 * - 首帧标记与"切换画质中"都只由入参决定，不做就地 latch 之后再被覆盖。
 */
fun derivePlaybackState(
    previous: PlaybackState,
    truth: PlaybackTruth,
    requests: PlaybackRequests,
    load: PlaybackLoadStatus = PlaybackLoadStatus.Idle,
): PlaybackState {
    val opening = load as? PlaybackLoadStatus.Opening
    // 开流意图只在后端还没走到终态前有效：走到 Ready 即视为本次开流完成，
    // 走到 Error 则由 Error 分支接管（否则 Error 会被 Preparing 盖住）。
    val loadActive = opening != null &&
        truth.phase != PlaybackPhase.Ready &&
        truth.phase != PlaybackPhase.Error
    val freshLoad = loadActive && opening?.isQualitySwitch == false

    val phase = when {
        load is PlaybackLoadStatus.Failed -> PlaybackPhase.Error
        truth.phase == PlaybackPhase.Error -> PlaybackPhase.Error
        loadActive -> PlaybackPhase.Preparing
        else -> truth.phase
    }

    val isBuffering = when (phase) {
        PlaybackPhase.Error, PlaybackPhase.Ended, PlaybackPhase.Idle -> false
        else -> loadActive || truth.isBuffering || truth.phase == PlaybackPhase.Preparing
    }

    return PlaybackState(
        phase = phase,
        isPlaying = truth.isPlaying && phase != PlaybackPhase.Error,
        isBuffering = isBuffering,
        positionMs = when {
            freshLoad -> 0L
            truth.positionMs > 0L -> truth.positionMs
            // 开流就绪前后端会瞬时回到 0（换流），沿用上一次免得进度条闪回起点
            else -> previous.positionMs
        },
        durationMs = when {
            freshLoad -> 0L
            truth.durationMs > 0L -> truth.durationMs
            // 媒体属性尚未就绪时不得被 0 覆盖
            else -> previous.durationMs
        },
        bufferedPositionMs = if (freshLoad) 0L else truth.bufferedPositionMs.coerceAtLeast(0L),

        requestedSpeed = requests.speed,
        actualSpeed = truth.speed ?: previous.actualSpeed,
        requestedAspect = requests.aspect,
        actualAspect = truth.aspect ?: previous.actualAspect,
        requestedPicture = requests.picture,
        actualPicture = truth.picture ?: previous.actualPicture,

        videoWidth = when {
            freshLoad -> 0
            truth.videoWidth > 0 -> truth.videoWidth
            // 尺寸未知时保留上一档（切画质保面）
            else -> previous.videoWidth
        },
        videoHeight = when {
            freshLoad -> 0
            truth.videoHeight > 0 -> truth.videoHeight
            else -> previous.videoHeight
        },
        // 单调锁存：只有开新片才清零。切画质不清 —— 否则画面会闪回海报。
        hasRenderedFirstFrame = when {
            freshLoad -> false
            truth.videoWidth > 0 && truth.videoHeight > 0 -> true
            else -> previous.hasRenderedFirstFrame
        },

        errorMessage = when {
            load is PlaybackLoadStatus.Failed -> load.message
            truth.phase == PlaybackPhase.Error ->
                truth.errorMessage ?: previous.errorMessage ?: DEFAULT_ERROR_MESSAGE
            else -> null
        },

        // 失败与到达终态都必须撤销"切换中"，否则它会永久卡在 true
        isSwitchingQuality = loadActive && opening?.isQualitySwitch == true,
    )
}

/** 是否刚进入 Error 相（引擎据此做一次性的恢复动作，如超分降档）。 */
fun enteredError(previous: PlaybackState, next: PlaybackState): Boolean =
    next.phase == PlaybackPhase.Error && previous.phase != PlaybackPhase.Error
