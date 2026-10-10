package lovehan1me.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * 轮播页指示器（从 BannerCarousel 抽出供复用）。
 *
 * 上游 misaka 为 `PageIndicator` 独立组件；此前本仓在 Banner 内手写 Row，
 * 公告轮播再写一遍又会漂。
 *
 * V1 重设计：选中态由 8dp 圆点改为 22dp 胶囊（M3 轮播指示器语言：当前页拉长），
 * 未选中保持 6dp 圆点；新增 [onPageClick]，传入即点点跳转（Banner 用它切页）。
 *
 * @param onPageClick null = 纯展示（无点击）；非 null = 胶囊/圆点均可点按跳转。
 */
@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    selectedColor: Color = Color.White,
    unselectedColor: Color = Color.White.copy(alpha = 0.5f),
    onPageClick: ((Int) -> Unit)? = null,
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
                    // 选中 22x6 胶囊 / 未选中 6dp 圆点；宽度动画过渡。
                    .width(if (isSelected) 22.dp else 6.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) selectedColor else unselectedColor)
                    .then(
                        if (onPageClick != null) {
                            Modifier.clickable(role = Role.Tab) { onPageClick(index) }
                        } else {
                            Modifier
                        }
                    )
                    .animateContentSize(),
            )
            if (index < pageCount - 1) Spacer(Modifier.width(6.dp))
        }
    }
}
