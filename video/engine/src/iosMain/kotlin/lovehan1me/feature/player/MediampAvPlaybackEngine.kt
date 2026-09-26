package lovehan1me.feature.player

import kotlinx.cinterop.ExperimentalForeignApi
import lovehan1me.core.util.LogUtil
import lovehan1me.video.contract.VideoEnhancementController
import org.openani.mediamp.avkit.AVKitMediampPlayer
import org.openani.mediamp.avkit.AVKitMediampPlayerFactory
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.volume

/**
 * Gate3-P5：iOS 换底 mediamp-avkit（v0.5.0）。
 *
 * 相比旧 iOS 引擎（`IosAVPlaybackEngine`，已随 Gate3-P6 删除）的收益：
 * - 状态机从"500ms 协程轮询推导"换成 mediamp 三轴真值（缓冲/seek 完成/错误即时）；
 * - 画面比例三档全真（它家 AVPlayerLayer.videoGravity，与本仓旧实现同机制，
 *   但状态来源统一到 mediamp feature）。
 *
 * 保持不变：
 * - Cookie 走共享 NSHTTPCookieStorage（登录态与系统行为一致）；
 * - ECH/自定义头：AVURLAsset 头路在 Xcode 26 SDK 已封死（见旧引擎 load 注释），
 *   mediamp-avkit 同样不走头——与换底前行为一致，待资源加载代理方案另立项；
 * - PiP：经 [IosPlayerPipBridge] 注册（每条视频一个引擎实例，release 时解绑）。
 * - 超分：iOS 无超分（与旧引擎一致，菜单自动隐藏）。
 */
@OptIn(ExperimentalForeignApi::class)
class MediampAvPlaybackEngine : MediampPlaybackEngineBase() {

    override val mediampPlayer: AVKitMediampPlayer = AVKitMediampPlayerFactory().create(
        parentCoroutineContext = scope.coroutineContext,
    )

    private val avPlayer: AVPlayer get() = mediampPlayer.impl as AVPlayer

    /** 显式表态：iOS 无 mpv/GL 超分链，不支持即 null（UI 入口整块隐藏）。 */
    override val enhancement: VideoEnhancementController? = null

    init {
        // 阶段一⑨：向画中画 holder 注册当前 AVPlayer（PiP 用独立 AVPlayerLayer，
        // 不碰渲染面的 layer）。
        IosPlayerPipBridge.onPlayerCreated?.invoke(avPlayer)
        startObserving()
    }

    override fun setVolume(volume: Float) {
        avPlayer.volume = volume.coerceIn(0f, 1f)
    }

    override fun onRelease() {
        IosPlayerPipBridge.onPlayerReleased?.invoke(avPlayer)
    }

    private companion object {
        const val TAG = "MediampAvEngine"
    }
}
