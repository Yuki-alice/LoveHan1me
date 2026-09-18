package lovehan1me.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import lovehan1me.core.util.LogUtil
import java.util.prefs.Preferences

/**
 * 桌面窗口几何持久化：尺寸 / 位置 / 是否最大化。
 *
 * ### 为什么用 `java.util.prefs` 而不是 DataStore
 *
 * 窗口必须在 DataStore 初始化**之前**就创建出来（[lovehan1me.desktop.Main] 的启动状态机
 * 是先建窗、再在 `LaunchedEffect` 里挂起初始化 DataStore）。所以这里必须是**同步**读；
 * 而且窗口几何是纯本机状态，进跨端备份（DataStore 会跟着备份走）没有意义。
 *
 * `java.util.prefs` 在 Windows 落注册表、macOS 落 `~/Library/Preferences`、Linux 落
 * `~/.java/.userPrefs`，都是 JVM 自带的，不额外引依赖。
 */
internal object WindowStateStore {
    private const val NODE = "lovehan1me/desktop/window"
    private const val KEY_X = "x"
    private const val KEY_Y = "y"
    private const val KEY_W = "width"
    private const val KEY_H = "height"
    private const val KEY_MAXIMIZED = "maximized"

    private const val DEFAULT_W = 1280f
    private const val DEFAULT_H = 800f

    private val prefs: Preferences get() = Preferences.userRoot().node(NODE)

    /**
     * 读上次窗口几何。
     *
     * 任何异常（首次启动、权限、注册表损坏）都回落到 1280x800 居中，
     * **绝不让窗口状态拖垮启动**。
     */
    fun load(): WindowGeometry = runCatching {
        val w = prefs.getFloat(KEY_W, DEFAULT_W)
        val h = prefs.getFloat(KEY_H, DEFAULT_H)
        val maximized = prefs.getBoolean(KEY_MAXIMIZED, false)

        val hasPosition = prefs.get(KEY_X, null) != null && prefs.get(KEY_Y, null) != null
        val position = if (hasPosition) {
            // 存的是 Dp 值（见 save 的说明）
            WindowPosition.Absolute(
                x = prefs.getFloat(KEY_X, 0f).dp,
                y = prefs.getFloat(KEY_Y, 0f).dp,
            )
        } else {
            WindowPosition.PlatformDefault
        }

        WindowGeometry(
            size = DpSize(w.dp, h.dp),
            position = position,
            placement = if (maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
        )
    }.getOrElse { e ->
        LogUtil.w("WindowStateStore", "load failed, fallback to default", e)
        WindowGeometry(
            size = DpSize(DEFAULT_W.dp, DEFAULT_H.dp),
            position = WindowPosition.PlatformDefault,
            placement = WindowPlacement.Floating,
        )
    }

    /**
     * 保存当前窗口几何。
     *
     * ⚠️ 存的是 **Dp 值不是像素**：同一台机器 density 稳定，跨机器换屏会有偏移，
     * 但换来的是不必在退出时再去查屏幕 density（退出路径越简单越不容易漏保存）。
     */
    fun save(state: WindowState) = runCatching {
        prefs.putFloat(KEY_W, state.size.width.value)
        prefs.putFloat(KEY_H, state.size.height.value)
        val pos = state.position
        if (pos is WindowPosition.Absolute) {
            prefs.putFloat(KEY_X, pos.x.value)
            prefs.putFloat(KEY_Y, pos.y.value)
        }
        prefs.putBoolean(KEY_MAXIMIZED, state.placement == WindowPlacement.Maximized)
        prefs.flush()
    }.onFailure { e ->
        LogUtil.w("WindowStateStore", "save failed", e)
    }
}

/** 一次读取出来的窗口几何快照，喂给 `rememberWindowState`。 */
internal data class WindowGeometry(
    val size: DpSize,
    val position: WindowPosition,
    val placement: WindowPlacement,
)

/**
 * 挂到 `Window` 的 content 里：退出时把几何落盘。
 *
 * 用 `onDispose` 而不是 `onCloseRequest`，是因为 `onCloseRequest` 可能被最小化到托盘
 * 之类的逻辑提前拦截；`onDispose` 只在窗口真正销毁时触发。
 */
@Composable
internal fun RememberWindowGeometry(state: WindowState) {
    DisposableEffect(state) {
        onDispose { WindowStateStore.save(state) }
    }
}
