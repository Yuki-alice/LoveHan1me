/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.support

import androidx.compose.ui.window.PopupProperties

/**
 * 三端 `PopupProperties` 构造参数集不一样（`usePlatformInsets` 只在 skiko 侧有，
 * `excludeFromSystemGesture` 只在 Android 侧有），common 代码只能调这个门面。
 */
@Suppress("FunctionName")
expect fun PlatformPopupPropertiesImpl(
    focusable: Boolean = false,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    usePlatformDefaultWidth: Boolean = false,
    // Android-only:
    excludeFromSystemGesture: Boolean = true,
    clippingEnabled: Boolean = true,
    // Desktop-only:
    usePlatformInsets: Boolean = true,
): PopupProperties

@Suppress("FunctionName")
fun PlatformPopupProperties(
    focusable: Boolean = false,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    usePlatformDefaultWidth: Boolean = false,
    excludeFromSystemGesture: Boolean = true,
    clippingEnabled: Boolean = true,
    usePlatformInsets: Boolean = true,
): PopupProperties = PlatformPopupPropertiesImpl(
    focusable = focusable,
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = dismissOnClickOutside,
    usePlatformDefaultWidth = usePlatformDefaultWidth,
    excludeFromSystemGesture = excludeFromSystemGesture,
    clippingEnabled = clippingEnabled,
    usePlatformInsets = usePlatformInsets,
)
