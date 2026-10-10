package lovehan1me.feature.home.homepage.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import lovehan1me.ui.theme.AppEmphasis
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.feature.home.homepage.HomeHeroItem
import lovehan1me.h_chan_load_failed
import lovehan1me.h_chan_loading
import lovehan1me.ui.component.PageIndicator
import lovehan1me.ui.component.PagerAutoScrollEffect
import lovehan1me.ui.component.RetryableImage
import org.jetbrains.compose.resources.painterResource

/**
 * 显示首页 Hero 轮播。
 *
 * 数据由官网运营位与下方分类行混排而成（见 `buildHomeHeroItems`）：单运营位时
 * 补视频使轮播可翻页。多于一项时无交互自动翻页（[PagerAutoScrollEffect]，
 * 拖动/悬停/后台暂停，移植 misaka 上游 + animeko hover 暂停）。
 *
 * 高度沿用本仓连续档（窄屏 16:9 不变矮、宽屏 21:9、中超宽封顶），不退回
 * 上游固定 16:9 —— 拖窗口改尺寸时高度不跳变。
 *
 * @param items 轮播数据，为空时不渲染。
 * @param onItemClick 点击项，参数为视频编号（运营位可能为空）。
 * @param pagerState 外提以支持外部切换（如未来右侧待播队列），默认内持。
 */
@Composable
fun BannerCarousel(
    items: List<HomeHeroItem>,
    onItemClick: (String?) -> Unit,
    modifier: Modifier = Modifier,
    pagerState: PagerState = rememberPagerState(pageCount = { items.size.coerceAtLeast(1) }),
) {
    if (items.isEmpty()) return

    // animeko 趋势轮播同款：桌面鼠标悬停暂停，移动端无 hover 无影响。
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    PagerAutoScrollEffect(
        pagerState = pagerState,
        pageCount = items.size,
        enabled = !isHovered,
    )

    Column(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val bannerHeight = minOf(
                maxWidth * 9f / 16f,
                maxOf(maxWidth * 9f / 21f, MIN_BANNER_HEIGHT),
                MAX_BANNER_HEIGHT,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bannerHeight)
                    .hoverable(hoverSource)
                    .clip(MaterialTheme.shapes.medium),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(0.dp),
                    pageSpacing = 0.dp,
                    beyondViewportPageCount = 1,
                ) { page ->
                    val item = items[page.coerceIn(items.indices)]
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onItemClick(item.videoCode) },
                    ) {
                        RetryableImage(
                            model = item.imageUrl,
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(Res.drawable.h_chan_loading),
                            error = painterResource(Res.drawable.h_chan_load_failed),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.7f),
                                        ),
                                    ),
                                ),
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                        ) {
                            Text(
                                text = item.title,
                                style = AppEmphasis.pageTitle,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.subtitle?.let { desc ->
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                PageIndicator(
                    pageCount = items.size,
                    currentPage = pagerState.currentPage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                )
            }
        }
    }
}

/**
 * 下限：窄屏按 16:9 走时不会矮过它（手机 400dp 宽 → 225dp 高，不受影响）。
 */
private val MIN_BANNER_HEIGHT = 320.dp

/**
 * 绝对封顶：内容宽 >1773dp 的超宽屏才动手，日常碰不到。
 */
private val MAX_BANNER_HEIGHT = 760.dp
