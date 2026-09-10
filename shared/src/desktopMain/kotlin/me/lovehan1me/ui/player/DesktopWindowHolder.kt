package me.lovehan1me.ui.player

import java.awt.Window

/**
 * M3：桌面主窗口持有者（`desktopApp/.../Main.kt` 的 `Window {}` 作用域内赋值，
 * 供 Skia 渲染面经 mediamp `findSkiaLayer(window)` 定位渲染层；
 * 同 `Han1meDatabaseContext` 的 holder 模式）。
 */
object DesktopWindowHolder {
    @Volatile
    var window: Window? = null
}
