package lovehan1me.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo

// 桌面窗口宽高比实时判定（替代恒 true；窗口拉窄后 banner 类布局可据此切竖排）。
actual @Composable
fun isLandscapeOrientation(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    return isLandscapeSize(size.width, size.height)
}
