package lovehan1me.core.platform

import androidx.compose.runtime.Composable

/** 横竖屏判定（原 android.content.res.Configuration.ORIENTATION_LANDSCAPE；桌面以宽屏近似） */
@Composable
expect fun isLandscapeOrientation(): Boolean
