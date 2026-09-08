package androidx.compose.ui.window

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import java.awt.Window

/**
 * M3：CMP 1.10 移除（1.12 亦无）的 `LocalWindow` 垫片。
 *
 * mediamp-mpv 0.3.2 的桌面渲染面（`MpvMediampPlayerSurface`，随 engine 同 jar）
 * 编译期引用本符号定位 SkiaLayer；运行期 CMP 不再提供，NoClassDefFoundError。
 * 此处按 1.10 形态补回（`ProvidableCompositionLocal<Window?>`，默认 null），
 * 由 `PlatformVideoSurface.desktop` 经 [io.github.daisukikaffuchino.han1meviewer.desktop.DesktopWindowHolder]
 * 注入真实窗口。待上游出 CMP 1.12 适配后删除本文件。
 */
val LocalWindow: ProvidableCompositionLocal<Window?> = compositionLocalOf { null }
