package lovehan1me.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import lovehan1me.core.platform.isLandscapeOrientation

// M3：iOS 暂无计量网络判定，降级。

actual fun isActiveNetworkMetered(): Boolean = false

/**
 * C4：iOS 转屏监听（对标 Android 陀螺仪；桌面无此概念，保持空实现）。
 *
 * 不走 `UIDevice` 方向通知，而直接看窗口宽高比：陀螺仪在"方向锁定开着但
 * 拿着手机平放"这类场景会误触发，窗口尺寸才是 UI 真正的依据；
 * 分屏/台前调度同样覆盖。调用方须先查 `supportsFullscreen()`：
 * 无全屏能力的平台绑定它只会制造"状态撒谎"（见 VideoRouteHostScreen 绑定处）。
 */
@Composable
actual fun BindOrientationAutoFullscreen(
    enabled: Boolean,
    onLandscape: () -> Unit,
    onPortrait: () -> Unit,
) {
    if (!enabled) return
    val landscape = isLandscapeOrientation()
    val latestLandscape by rememberUpdatedState(onLandscape)
    val latestPortrait by rememberUpdatedState(onPortrait)
    LaunchedEffect(landscape) {
        if (landscape) latestLandscape() else latestPortrait()
    }
}
