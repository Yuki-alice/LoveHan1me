package lovehan1me.video.ui

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 播放器控件在本模块内自用的 token 子集。
 *
 * 值与 `shared/src/commonMain/kotlin/lovehan1me/ui/theme/Defaults.kt` 的 `HanimeDefaults`
 * 逐条对应，只搬控件真正读到的那些名字；换 token 不改像素。
 *
 * 名字刻意**不叫** `HanimeDefaults`：同名的第二套常量在编译期不报错，只会让
 * 「改一处全局生效」静默失效（`Defaults.kt:17-24` 记过这次事故）。
 */
object PlayerTokens {
    object Spacing {
        val small = 4.dp
        val medium = 8.dp
        val large = 12.dp
        val extraLarge = 16.dp
        val extraExtraLarge = 24.dp
        val huge = 32.dp
    }

    object Sizes {
        val controlXS = 32.dp
    }

    object OverlayAlpha {
        const val border = 0.06f
        const val divider = 0.12f
        const val track = 0.14f
        const val trackBuffered = 0.5f
        const val scrim = 0.75f
        const val scrimDeep = 0.82f
        const val previewBubble = 0.82f
        const val blurDim = 0.32f
        const val videoDim = 0.46f
        const val textFaint = 0.04f
        const val textTertiary = 0.72f
        const val textStrong = 0.92f
        const val textPrimary = 0.95f
    }

    object Overlay {
        val onScrim = Color.White
        val backdrop = Color.Black

        val border = Color.White.copy(alpha = OverlayAlpha.border)
        val divider = Color.White.copy(alpha = OverlayAlpha.divider)
        val track = Color.White.copy(alpha = OverlayAlpha.track)
        val trackBuffered = Color.White.copy(alpha = OverlayAlpha.trackBuffered)

        val previewBubble = Color.Black.copy(alpha = OverlayAlpha.previewBubble)

        val scrimTopStart = Color.Transparent
        val scrimTopEnd = Color.Black.copy(alpha = OverlayAlpha.scrim)
        val scrimBottomStart = Color.Transparent
        val scrimBottomEnd = Color.Black.copy(alpha = OverlayAlpha.scrimDeep)

        val blurDim = Color.Black.copy(alpha = OverlayAlpha.blurDim)
        val videoDim = Color.Black.copy(alpha = OverlayAlpha.videoDim)

        val textFaint = Color.White.copy(alpha = OverlayAlpha.textFaint)
        val textTertiary = Color.White.copy(alpha = OverlayAlpha.textTertiary)
        val textStrong = Color.White.copy(alpha = OverlayAlpha.textStrong)
        val textPrimary = Color.White.copy(alpha = OverlayAlpha.textPrimary)
    }

    object PlayerSizes {
        /** 触控目标下限（M3 硬指标）。 */
        val minTouchTarget = 48.dp
        val scrimTop = 120.dp
        val scrimBottom = 180.dp

        /** 底栏控制行的**布局**高度：行内要容得下进度条的命中区，故按 [minTouchTarget] 给。 */
        val bottomControlRow = minTouchTarget

        val centerButton = 72.dp
        val centerIcon = 42.dp
        val lockButton = 48.dp
        val iconLarge = 24.dp
        val bottomPrimaryIcon = 36.dp
        val bottomSecondaryIcon = 32.dp

        val track = 6.dp
        val trackBox = 22.dp
        val thumbBox = 16.dp
        val thumb = 16.dp
    }

    object Corners {
        /**
         * 胶囊/圆形 —— percent=50。
         *
         * 必须是**具体的 shape 构造**，写成 token 名会自引用并无限递归。
         */
        val pill: CornerBasedShape
            get() = RoundedCornerShape(percent = 50)
    }

    object Colors {
        val pageSurface: Color
            @Composable get() = MaterialTheme.colorScheme.surface
    }
}