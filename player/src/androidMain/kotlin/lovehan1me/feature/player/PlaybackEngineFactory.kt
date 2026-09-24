package lovehan1me.feature.player

import android.content.Context
import lovehan1me.data.database.dao.Han1meDatabaseContext

object PlaybackEngineFactory {
    fun create(
        context: Context,
        kernel: PlayerKernel,
        network: PlayerNetworkConfig,
        mpvOptions: PlayerMpvOptionsProvider,
    ): PlaybackEngine = when (kernel) {
        // Gate3-P5：System 引擎已删（mediamp 无对应）；MediaPlayer 档降级到 mediamp-exo。
        PlayerKernel.MediaPlayer, PlayerKernel.ExoPlayer -> MediampExoPlaybackEngine(context, network)
        // mpv 内核保留为唯一例外（产品决策 2026-09-24：switch_player_kernel 现有功能不动，
        // 砍留待 P6 收尾再议）。
        PlayerKernel.MpvPlayer -> MpvPlaybackEngine(context, network, mpvOptions)
    }
}

// P5-1：expect 入口的 Android 实现。Context 取共享层既有 holder（DataStoreManager.initialize
// 经 Han1meApplication.onCreate 注入的 applicationContext），调用方无需传 Context。
actual fun createPlaybackEngine(
    kernel: PlayerKernel,
    network: PlayerNetworkConfig,
    mpvOptions: PlayerMpvOptionsProvider,
): PlaybackEngine = PlaybackEngineFactory.create(
    context = Han1meDatabaseContext.appContext,
    kernel = kernel,
    network = network,
    mpvOptions = mpvOptions,
)
