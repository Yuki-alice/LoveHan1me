package lovehan1me.core.platform

import androidx.compose.runtime.Composable

/** 横竖屏判定（原 android.content.res.Configuration.ORIENTATION_LANDSCAPE；桌面/iOS 以窗口宽高比判定） */
@Composable
expect fun isLandscapeOrientation(): Boolean

/**
 * 纯宽高比判定（可单测；未知尺寸默认竖屏）。
 *
 * 正方形算竖屏，与 Android `Configuration` 语义一致：只有明确更宽才算横屏。
 * 宽高任一非法（预布局的 0 尺寸）时返回 false，不撒谎。
 */
fun isLandscapeSize(width: Int, height: Int): Boolean =
    width > 0 && height > 0 && width > height
