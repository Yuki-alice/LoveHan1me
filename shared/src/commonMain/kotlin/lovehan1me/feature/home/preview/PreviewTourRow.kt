package lovehan1me.feature.home.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import lovehan1me.ui.adaptive.rememberContentWidthDp
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.core.domain.model.HanimeInfo
import lovehan1me.ui.component.lazy.LazyRow
import kotlinx.coroutines.launch

/**
 * 预览游览行。水平滚动展示当月最新影片缩略图，选中项高亮。
 *
 * @param latestHanime 影片列表
 * @param selectedIndex 当前选中项索引
 * @param onSelect 选中回调
 */
@Composable
fun PreviewTourRow(
    latestHanime: List<HanimeInfo>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 居中留白按**内容区可用宽度**计算（常驻抽屉占宽已扣除）；用整窗宽度会让
    // 卡片在宽窗下偏离中心。
    val contentWidthDp = rememberContentWidthDp()
    val edgePadding = remember(contentWidthDp) {
        ((contentWidthDp - 92.dp) / 2).coerceAtLeast(16.dp)
    }

    LaunchedEffect(selectedIndex, latestHanime.size) {
        if (latestHanime.isEmpty()) return@LaunchedEffect
        centerPreviewTourItem(listState, selectedIndex)
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = edgePadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(
            latestHanime,
            key = { index, item -> item.videoCode.ifBlank { "tour-$index" } },
        ) { index, item ->
            OutlinedCard(
                onClick = {
                    onSelect(index)
                    scope.launch {
                        centerPreviewTourItem(listState, index)
                    }
                },
                shape = RoundedCornerShape(18.dp),
            ) {
                Box {
                    HanimeAsyncImage(
                        model = item.coverUrl,
                        contentDescription = item.title,
                        modifier = Modifier
                            .width(92.dp)
                            .height(132.dp),
                        contentScale = ContentScale.Crop,
                    )
                    if (index == selectedIndex) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                        )
                    }
                }
            }
        }
    }
}
