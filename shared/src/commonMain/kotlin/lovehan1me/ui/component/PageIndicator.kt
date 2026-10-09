package lovehan1me.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 轮播页指示器（圆点条，从 BannerCarousel 抽出供复用）。
 *
 * 上游 misaka 为 `PageIndicator` 独立组件；此前本仓在 Banner 内手写 Row，
 * 公告轮播再写一遍又会漂。选中 8dp/未选中 6dp 沿用既有视觉。
 */
@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    selectedColor: Color = Color.White,
    unselectedColor: Color = Color.White.copy(alpha = 0.5f),
) {
    if (pageCount <= 1) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isSelected = currentPage == index
            Box(
                modifier = Modifier
                    .size(if (isSelected) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) selectedColor else unselectedColor),
            )
            if (index < pageCount - 1) Spacer(Modifier.width(6.dp))
        }
    }
}
