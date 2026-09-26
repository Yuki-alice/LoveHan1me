package lovehan1me.feature.player

import platform.AVFoundation.AVPlayer

/**
 * Gate3-P5：:video:engine → :shared 的 PiP 播放器注册桥。
 *
 * 依赖方向是 :shared 消费 :video:engine，引擎（在 :video:engine）拿不到 :shared 的
 * `IosPipPlayerHolder` —— P1 搬迁时旧 `IosAVPlaybackEngine` 直接引用它，
 * 是遗留的反向依赖（本地离线编 iOS 未暴露，P5 已修）。改为：引擎只调本桥，
 * :shared 侧（IosVideoPageHost）启动时把 holder 的 attach/detach 注入进来。
 * 现存唯一实现方是 [MediampAvPlaybackEngine]。
 */
object IosPlayerPipBridge {
    internal var onPlayerCreated: ((AVPlayer) -> Unit)? = null
    internal var onPlayerReleased: ((AVPlayer) -> Unit)? = null

    fun register(
        onPlayerCreated: (AVPlayer) -> Unit,
        onPlayerReleased: (AVPlayer) -> Unit,
    ) {
        this.onPlayerCreated = onPlayerCreated
        this.onPlayerReleased = onPlayerReleased
    }
}
