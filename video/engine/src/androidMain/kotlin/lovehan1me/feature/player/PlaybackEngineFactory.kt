package lovehan1me.feature.player

import android.content.Context
import lovehan1me.data.database.dao.Han1meDatabaseContext

/**
 * Gate3-P5：System 引擎已删（mediamp 无对应）；MediaPlayer 档降级到 mediamp-exo。
 * Gate3-P6：mpv 内核已砍——[kernel] 在 Android 成为**惰性参数**（与桌面/iOS 同性质）。
 * 历史存下的 `switch_player_kernel = MpvPlayer` 不再改变引擎选择、也不会崩，
 * 一律返回 mediamp-exo；`mpvOptions` 同样不再有消费者（桌面仍用，见 desktopMain）。
 * 两参数保留是为了与 `expect` 签名统一，收敛它们属 Gate5 仓库卫生。
 */
object PlaybackEngineFactory {
    fun create(
        context: Context,
        kernel: PlayerKernel,
        network: PlayerNetworkConfig,
        mpvOptions: PlayerMpvOptionsProvider,
    ): PlaybackEngine = MediampExoPlaybackEngine(context, network)
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
