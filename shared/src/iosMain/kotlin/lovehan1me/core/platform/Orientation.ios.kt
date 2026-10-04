package lovehan1me.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo

// iOS 按宿主视图宽高比实时判定（替代恒 false；分屏/横屏时布局可据此响应）。
actual @Composable
fun isLandscapeOrientation(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    return isLandscapeSize(size.width, size.height)
}
