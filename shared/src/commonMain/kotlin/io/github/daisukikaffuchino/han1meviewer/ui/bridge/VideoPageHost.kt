package io.github.daisukikaffuchino.han1meviewer.ui.bridge

import androidx.compose.ui.geometry.Rect

/**
 * M3：视频页宿主的平台窗口能力。
 *
 * 原 `:app` `VideoRouteHostScreen` 把 PiP / 常亮 / 全屏 / 亮度 / 系统栏全部直调
 * `MainActivity.window`；下沉后窗口操作收敛到本接口（默认空实现）：
 * - Android（`:app` `AndroidVideoPageHost`）：搬原 Host 代码（PiP RemoteAction、
 *   FLAG_KEEP_SCREEN_ON、requestedOrientation、systemBars、亮度读写、host 注册）；
 * - 桌面/iOS：[NoopVideoPageHost]（窗口全屏/画中画随 M-后续增强）。
 *
 * 纯逻辑（评论徽标、PiP 准入判断、播放切换）由共享 Host 内部实现，不经过本接口。
 */
interface VideoPageHost {
    fun showCommentBadge(count: Int)
    fun shouldEnterPip(): Boolean
    fun enterPipMode()
    fun onPipModeChanged(isInPip: Boolean)
    fun togglePlayPause()

    /**
     * 进入画中画（带当前播放态与切换按钮描述，供 RemoteAction 用）。
     * 播放器在窗口中的位置经 [setPipSourceRect] 预先传入。
     */
    fun enterPipMode(isPlaying: Boolean, toggleDescription: String) {}

    /** 播放状态变化时刷新 PiP RemoteAction（暂停/播放图标），非 PiP 下可空实现。 */
    fun refreshPipAction(isPlaying: Boolean, toggleDescription: String) {}

    /** 全屏切换（含横竖屏与系统栏），UI 侧 isFullscreen 状态由调用方维护。 */
    fun applyFullscreen(fullscreen: Boolean, forceLandscape: Boolean = false) {}

    /** 当前屏幕亮度（0.01~1），不支持读取的平台返回 0.5f。 */
    fun currentBrightness(): Float = 0.5f

    /** 设置屏幕亮度，不支持的平台空实现。 */
    fun applyBrightness(value: Float) {}

    /** 播放器在窗口中的位置（PiP sourceRectHint 用），不支持的平台可忽略。 */
    fun setPipSourceRect(rect: Rect?) {}

    /** Host 进入/离开前台（注册广播、常亮锁、初始系统栏样式 / 反向清理）。 */
    fun onHostStarted() {}
    fun onHostStopped() {}
}

/** 桌面/iOS 默认实现：全部空操作（纯逻辑由共享 Host 内部对象承担）。 */
object NoopVideoPageHost : VideoPageHost {
    override fun showCommentBadge(count: Int) {}
    override fun shouldEnterPip(): Boolean = false
    override fun enterPipMode() {}
    override fun onPipModeChanged(isInPip: Boolean) {}
    override fun togglePlayPause() {}
}
