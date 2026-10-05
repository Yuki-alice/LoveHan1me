@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package lovehan1me.feature.player

import kotlinx.cinterop.ExperimentalForeignApi
import lovehan1me.app.bridge.PipModeReporter
import lovehan1me.app.bridge.VideoPageHost
import lovehan1me.core.platform.playerPlatformCapabilities
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
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIScreen
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
 * - 播放器 `AVPlayer` 由引擎（Gate3-P5 起是 `MediampAvPlaybackEngine`）经
 *   [IosPipPlayerHolder] 注册（引擎每条视频一个实例，release 时解绑）；
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

// Gate3-P5：引擎侧（:video:engine）经 IosPlayerPipBridge 注册 PiP 播放器（依赖方向
// :shared → :video:engine，引擎拿不到本文件符号）。此注入是 bridge 的唯一接线点。
private val pipBridgeWiring: Unit = run {
    IosPlayerPipBridge.register(
        onPlayerCreated = { IosPipPlayerHolder.attach(it) },
        onPlayerReleased = { IosPipPlayerHolder.detach(it) },
    )
    Unit
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

    /**
     * C1a：亮度是真实现（`UIScreen.mainScreen.brightness` 读写），故声明支持。
     *
     * 调用方（手势 HUD）全在 Compose 主线程，与 Android 直接读写窗口同约束。
     * 真值取自平台能力表（[playerPlatformCapabilities]）。
     */
    override fun supportsBrightness(): Boolean = playerPlatformCapabilities().brightness

    override fun currentBrightness(): Float =
        UIScreen.mainScreen.brightness.toFloat().coerceIn(0.01f, 1f)

    override fun applyBrightness(value: Float) {
        UIScreen.mainScreen.brightness = value.coerceIn(0.01f, 1f).toDouble()
    }

    /**
     * C1b：iOS 全屏已实现（手动路线：布局态复用 common `isFullscreen`，
     * 系统栏/方向经 [IosFullscreenBridge] 交 Swift 壳，PiP 层不动）。
     * 真值取自平台能力表（[playerPlatformCapabilities]）。
     */
    override fun supportsFullscreen(): Boolean = playerPlatformCapabilities().fullscreen

    override fun applyFullscreen(fullscreen: Boolean, forceLandscape: Boolean) {
        IosFullscreenBridge.publish(fullscreen, forceLandscape)
    }

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
        // C1a：播放页常亮（对标 Android FLAG_KEEP_SCREEN_ON；离开页面即恢复）。
        UIApplication.sharedApplication.idleTimerDisabled = true
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
        UIApplication.sharedApplication.idleTimerDisabled = false
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
        // 模拟器无 PiP 硬件语义：控制器构造直接返回 nil（Kotlin 侧即 NPE），
        // 且此前无任何守卫——点开视频页必闪退（实测）。无支持直接跳过，
        // 渲染面用自己的 AVPlayerLayer，不受影响。
        if (!AVPictureInPictureController.isPictureInPictureSupported()) {
            LogUtil.d(TAG, "PiP 不支持，跳过控制器")
            return
        }
        val layer = runCatching { AVPlayerLayer.playerLayerWithPlayer(player) }.getOrNull() ?: run {
            LogUtil.w(TAG, "AVPlayerLayer 创建失败，跳过 PiP 控制器")
            return
        }
        val controller = runCatching { AVPictureInPictureController(playerLayer = layer) }.getOrNull() ?: run {
            LogUtil.w(TAG, "PiP 控制器创建失败，跳过")
            return
        }
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
