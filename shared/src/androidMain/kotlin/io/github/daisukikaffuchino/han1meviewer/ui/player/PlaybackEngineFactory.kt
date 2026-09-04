package io.github.daisukikaffuchino.han1meviewer.ui.player

import android.content.Context
import io.github.daisukikaffuchino.han1meviewer.logic.dao.Han1meDatabaseContext

object PlaybackEngineFactory {
    fun create(
        context: Context,
        kernel: PlayerKernel,
        allowCast: Boolean = true,
    ): PlaybackEngine {
        val localEngine = when (kernel) {
        PlayerKernel.MediaPlayer -> SystemPlaybackEngine(context)
        PlayerKernel.ExoPlayer -> ExoPlaybackEngine(context)
        PlayerKernel.MpvPlayer -> MpvPlaybackEngine(context)
        }
        return if (allowCast) {
            CastPlaybackEngine.createOrLocal(context, localEngine)
        } else {
            localEngine
        }
    }
}

// P5-1：expect 入口的 Android 实现。Context 取共享层既有 holder（DataStoreManager.initialize
// 经 HanimeApplication.onCreate 注入的 applicationContext），调用方无需传 Context。
actual fun createPlaybackEngine(
    kernel: PlayerKernel,
    allowCast: Boolean,
): PlaybackEngine = PlaybackEngineFactory.create(
    context = Han1meDatabaseContext.appContext,
    kernel = kernel,
    allowCast = allowCast,
)
