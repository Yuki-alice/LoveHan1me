package lovehan1me.feature.player

import lovehan1me.app.bridge.VideoPageHost
import java.awt.GraphicsEnvironment

/**
 * M5-2：桌面视频页的窗口宿主。
 *
 * 支持的能力：全屏切换（AWT `GraphicsDevice.setFullScreenWindow`，
 * 窗口来自 [DesktopWindowHolder]，由 `desktopApp` 的 `Window {}` 注入）。
 *
 * 暂不支持：画中画（桌面多窗口语义不同于移动端 PiP）、屏幕亮度（无法调节显示器
 * 硬件亮度）、系统栏（桌面无此概念）——均为 [VideoPageHost] 的默认空实现。
 */
class DesktopVideoPageHost : VideoPageHost {

    override fun showCommentBadge(count: Int) {
        // 桌面无启动器角标
    }

    override fun shouldEnterPip(): Boolean = false

    override fun enterPipMode() {}

    override fun onPipModeChanged(isInPip: Boolean) {}

    override fun togglePlayPause() {
        // 桌面暂无全局媒体键/通知栏控制入口
    }

    override fun applyFullscreen(fullscreen: Boolean, forceLandscape: Boolean) {
        val window = DesktopWindowHolder.window ?: return
        val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        if (fullscreen) {
            device.setFullScreenWindow(window)
        } else if (device.fullScreenWindow === window) {
            device.setFullScreenWindow(null)
        }
    }
}
