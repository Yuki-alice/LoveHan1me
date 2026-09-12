package lovehan1me.feature.home.homepage.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import lovehan1me.Res
import lovehan1me.more
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.ui.component.VideoCardItem
import lovehan1me.ui.adaptive.rememberCategoryMatrix
import lovehan1me.ui.adaptive.rememberPageHorizontalMargin
import lovehan1me.ui.theme.HanimeDefaults

/**
 * 首页分类区块（P4）：**响应式矩阵**，替代原先的横向滚动 `CategoryRow`。
 *
 * 为什么改掉横向滚动：鼠标滚轮是纵向的。桌面用户要看第 6 张卡片得按住 Shift+滚轮，
 * 而宽屏（1920dp）明明有横向空间却只显示 5.1 张卡 —— 这是把移动端的手势惯例
 * 硬套到了桌面上。现在宽屏直接铺成 5 列网格，一次看全。
 *
 * 矩阵档位由 **可用内容宽度** 驱动（[rememberCategoryMatrix]），不是窗口断点 ——
 * 常驻 Rail 会吃掉 80–220dp，用窗口宽度分档会在宽屏上误判。
 *
 * 截断规则：**只显示「列 × 行」能放下的，其余收进「更多」**；数据不足则少显示，
 * 不补空位、不凑满换行（末行不满时留白，不渲染占位卡）。
 */
@Composable
fun CategoryBlock(
    title: String,
    videos: List<HanimeInfo>,
    onMoreClick: () -> Unit,
    onVideoClick: (String) -> Unit,
    onVideoLongClick: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val margin = rememberPageHorizontalMargin()
    val matrix = rememberCategoryMatrix()
    // 同一分类在数据源里可能重复（首页多个分区取同一批视频），去重后再截断。
    val visible = videos.distinctBy { it.videoCode }.take(matrix.capacity)

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = margin, vertical = HanimeDefaults.Spacing.small),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.more),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onMoreClick() }
                    .padding(horizontal = HanimeDefaults.Spacing.large, vertical = HanimeDefaults.Spacing.small),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = margin),
            verticalArrangement = Arrangement.spacedBy(matrix.spacing),
        ) {
            repeat(matrix.rows) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(matrix.spacing)) {
                    repeat(matrix.columns) { column ->
                        val index = row * matrix.columns + column
                        if (index < visible.size) {
                            VideoCardItem(
                                videoItem = visible[index],
                                isHorizontalCard = true,
                                isHomePage = true,
                                onClickVideosItem = onVideoClick,
                                onLongClickVideosItem = onVideoLongClick,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            // 末行不满：留白而非渲染占位卡（「不补空位」）。
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
