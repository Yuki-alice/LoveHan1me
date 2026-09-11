@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package lovehan1me.feature.player

import kotlinx.cinterop.ExperimentalForeignApi
import lovehan1me.app.bridge.PipModeReporter
import lovehan1me.app.bridge.VideoPageHost
import lovehan1me.core.util.LogUtil
import lovehan1me.data.SettingsRepository
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.currentItem
import platform.AVFoundation.rate
import platform.AVKit.AVPictureInPictureController
import platform.AVKit.AVPictureInPictureControllerDelegateProtocol
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.darwin.NSObject

/**
 * 阶段一⑨：iOS 画中画（AVPictureInPictureController，对标 Android 的
 * `onUserLeaveHint` 自动进画中画）。
 *
 * 触发：iOS 无 Home 手势回调，用 `didEnterBackground` 通知等价
 * （锁屏/切后台/上滑挂起；控制中心/通知栏只 resignActive 不进后台，不会误触）。
 * 进入条件与 Android 同：设置允许 + 正在播（rate != 0 且有 currentItem）。
 *
 * 管线归属：
 * - 播放器 `AVPlayer` 由 [IosAVPlaybackEngine] 经 [IosPipPlayerHolder] 注册
 *   （引擎每条视频一个实例，release 时解绑）；
 * - PiP 用**独立** `AVPlayerLayer`（不复用渲染面的 layer——面的 layer 随
 *   Compose 释放，PiP 窗必须在页面销毁后继续活）；
 * - 状态经 [PipModeReporter.pipModeListener] 回共享 pageHost
 *   → `VideoViewModel.setPipMode`（后台不暂停就靠它）；
 * - 后台音频 entitlement（`audio`）见 `iosApp/Info.plist`，进入时顺手把
 *   AVAudioSession 切到 playback（best-effort，失败只记 log）。
 *
 * 未验证：真机（模拟器无 PiP 硬件语义，`isPictureInPictureSupported`
 * 在模拟器恒 false，只能编译级保证）。
 */
object IosPipPlayerHolder {
    private const val TAG = "IosPip"

    var avPlayer: AVPlayer? = null
        private set

    fun attach(player: AVPlayer) {
        if (avPlayer !== player) {
            IosVideoPageHost.rebuildForPlayer(player)
        }
        avPlayer = player
    }

    fun detach(player: AVPlayer) {
        if (avPlayer === player) {
            avPlayer = null
            IosVideoPageHost.releaseController()
        }
    }
}

object IosVideoPageHost : VideoPageHost, PipModeReporter {
    private const val TAG = "IosPip"

    override var pipModeListener: ((Boolean) -> Unit)? = null

    private var pipController: AVPictureInPictureController? = null
    private var pipLayer: AVPlayerLayer? = null
    private var pipPlayer: AVPlayer? = null
    private var pipDelegate: NSObject? = null
    private var backgroundObserver: Any? = null

    override fun showCommentBadge(count: Int) {}
    override fun togglePlayPause() {}
    override fun onPipModeChanged(isInPip: Boolean) {}

    override fun shouldEnterPip(): Boolean {
        val player = IosPipPlayerHolder.avPlayer ?: return false
        if (!AVPictureInPictureController.isPictureInPictureSupported()) return false
        return currentController(player)?.isPictureInPicturePossible() == true
    }

    override fun enterPipMode() {
        startPip()
    }

    override fun enterPipMode(isPlaying: Boolean, toggleDescription: String) {
        startPip()
    }

    override fun onHostStarted() {
        val center = NSNotificationCenter.defaultCenter
        backgroundObserver = center.addObserverForName(
            UIApplicationDidEnterBackgroundNotification,
            null,
            NSOperationQueue.mainQueue(),
        ) { _ ->
            autoEnterOnBackground()
        }
    }

    override fun onHostStopped() {
        backgroundObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        backgroundObserver = null
        stopPip()
    }

    /** 退后台自动进（对标 Android onUserLeaveHint；尊重 allowPipMode 开关）。 */
    private fun autoEnterOnBackground() {
        if (!SettingsRepository.current.allowPipMode) return
        val player = IosPipPlayerHolder.avPlayer ?: return
        if (player.currentItem == null || player.rate == 0f) return
        startPip()
    }

    private fun startPip() {
        val player = IosPipPlayerHolder.avPlayer ?: run {
            LogUtil.w(TAG, "startPip: no player")
            return
        }
        if (!AVPictureInPictureController.isPictureInPictureSupported()) {
            LogUtil.w(TAG, "startPip: PiP not supported on this device")
            return
        }
        val controller = currentController(player) ?: return
        if (!controller.isPictureInPicturePossible()) {
            LogUtil.w(TAG, "startPip: PiP not possible right now")
            return
        }
        setPlaybackAudioSession()
        controller.startPictureInPicture()
    }

    private fun stopPip() {
        val controller = pipController ?: return
        if (controller.isPictureInPictureActive()) {
            controller.stopPictureInPicture()
        }
    }

    internal fun rebuildForPlayer(player: AVPlayer) {
        releaseController()
        val layer = AVPlayerLayer.playerLayerWithPlayer(player)
        val controller = AVPictureInPictureController(playerLayer = layer)
        val delegate = object : NSObject(), AVPictureInPictureControllerDelegateProtocol {
            override fun pictureInPictureControllerWillStartPictureInPicture(
                controller: AVPictureInPictureController,
            ) {
                pipModeListener?.invoke(true)
            }

            override fun pictureInPictureControllerDidStopPictureInPicture(
                controller: AVPictureInPictureController,
            ) {
                pipModeListener?.invoke(false)
            }

            override fun pictureInPictureController(
                controller: AVPictureInPictureController,
                restoreUserInterfaceForPictureInPictureStopWithCompletionHandler: (Boolean) -> Unit,
            ) {
                // 单窗口 Compose 应用：点 PiP 窗回前台即回到视频页，直接完成
                restoreUserInterfaceForPictureInPictureStopWithCompletionHandler(true)
            }

            override fun pictureInPictureController(
                controller: AVPictureInPictureController,
                failedToStartPictureInPictureWithError: NSError,
            ) {
                LogUtil.e(
                    TAG,
                    "PiP failed: ${failedToStartPictureInPictureWithError.localizedDescription}",
                )
                pipModeListener?.invoke(false)
            }
        }
        controller.delegate = delegate
        pipLayer = layer
        pipPlayer = player
        pipController = controller
        pipDelegate = delegate
    }

    internal fun releaseController() {
        stopPip()
        pipController?.delegate = null
        pipController = null
        pipLayer = null
        pipPlayer = null
        pipDelegate = null
    }

    private fun currentController(player: AVPlayer): AVPictureInPictureController? {
        val existing = pipController
        if (existing != null && pipPlayer === player) return existing
        return try {
            rebuildForPlayer(player)
            pipController
        } catch (e: Exception) {
            LogUtil.e(TAG, "rebuild PiP controller failed", e)
            null
        }
    }

    private fun setPlaybackAudioSession() {
        // Best-effort：切 playback 分类（锁屏/后台不断音）。
        // 注：`setActive(true)` 在当前 KN 绑定下解析不到，先不调——
        // AVPlayer 起播时会隐式激活会话，真机若断音再回头处理。
        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
        }.onFailure { LogUtil.e(TAG, "audio session failed", it) }
    }
}
