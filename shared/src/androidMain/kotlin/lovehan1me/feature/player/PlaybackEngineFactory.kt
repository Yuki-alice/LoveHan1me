package lovehan1me.feature.player

import android.content.Context
import lovehan1me.data.database.dao.Han1meDatabaseContext

object PlaybackEngineFactory {
    fun create(
        context: Context,
        kernel: PlayerKernel,
    ): PlaybackEngine = when (kernel) {
        PlayerKernel.MediaPlayer -> SystemPlaybackEngine(context)
        PlayerKernel.ExoPlayer -> ExoPlaybackEngine(context)
        PlayerKernel.MpvPlayer -> MpvPlaybackEngine(context)
    }
}

// P5-1：expect 入口的 Android 实现。Context 取共享层既有 holder（DataStoreManager.initialize
// 经 HanimeApplication.onCreate 注入的 applicationContext），调用方无需传 Context。
actual fun createPlaybackEngine(
    kernel: PlayerKernel,
): PlaybackEngine = PlaybackEngineFactory.create(
    context = Han1meDatabaseContext.appContext,
    kernel = kernel,
)
