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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import lovehan1me.ui.theme.AppEmphasis
import lovehan1me.ui.theme.HanimeDefaults
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.watch_now
import lovehan1me.ic_play_arrow
import lovehan1me.feature.home.homepage.HomeHeroItem
import lovehan1me.h_chan_load_failed
import lovehan1me.h_chan_loading
import lovehan1me.ui.component.PageIndicator
import lovehan1me.ui.component.PagerAutoScrollEffect
import lovehan1me.ui.component.RetryableImage
import lovehan1me.ui.component.rememberHapticFeedback
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch

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
 * V1 重设计（角标按要求不上）：CTA「立即观看」（`videoCode` 为 null 的运营位
 * 不显示，与整卡点击语义一致）、指示器胶囊化 + 点点跳转、遮罩改底部比例渐变、
 * 圆角 medium → 卡片同档 large、宽屏封顶 760 → 440（释放桌面首屏）。
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
    val haptic = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    PagerAutoScrollEffect(
        pagerState = pagerState,
        pageCount = items.size,
        enabled = !isHovered,
    )

    Column(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 三取小：宽高比档 × 440 绝对封顶 × 视口 40% 封顶。
            // LocalWindowInfo.containerSize 是窗口内容区像素（Desktop/AWT 内容区、
            // Android/iOS 窗口），转 dp 后取 40%；取不到（≤0）时纯函数内部退化。
            // 拖窗口改尺寸时三项都连续，高度不跳变。
            val density = LocalDensity.current
            val viewportHeight = with(density) {
                LocalWindowInfo.current.containerSize.height.toDp()
            }
            val bannerHeight = bannerHeightFor(maxWidth, viewportHeight)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bannerHeight)
                    .hoverable(hoverSource)
                    // hero 层级高于普通视频卡：圆角取卡片同档 large（20dp），不再用 medium。
                    .clip(HanimeDefaults.Corners.large),
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
                            .clickable {
                                haptic()
                                onItemClick(item.videoCode)
                            },
                    ) {
                        RetryableImage(
                            model = item.imageUrl,
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(Res.drawable.h_chan_loading),
                            error = painterResource(Res.drawable.h_chan_load_failed),
                        )
                        // 底部遮罩改比例渐变：此前固定 120dp，高 banner 下只盖底边一条、
                        // 标题可读性看图片脸色。现在铺满并从 45% 起渐显，任意高度可读。
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.45f to Color.Transparent,
                                            1f to Color.Black.copy(alpha = 0.75f),
                                        ),
                                    ),
                                ),
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(16.dp)
                                // 右下 CTA 常驻时标题区右端预留 CTA 宽度 + 间距，
                                // 窄屏下两者不重叠；无 CTA 时不预留（运营位标题吃满宽）。
                                .padding(end = if (item.videoCode != null) 132.dp else 0.dp),
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
                        // CTA 右下：与整卡同动作（haptic 同步），只是更大的命中区。
                        // videoCode 为 null 的运营位不显示（点了也没处去）。
                        if (item.videoCode != null) {
                            Button(
                                onClick = {
                                    haptic()
                                    onItemClick(item.videoCode)
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 16.dp, bottom = 16.dp),
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_play_arrow),
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(HanimeDefaults.Spacing.medium))
                                Text(
                                    text = stringResource(Res.string.watch_now),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                // 指示器放右上：底部一行左标题 + 右 CTA 已占满，
                // 居中圆点在 ≤360dp 屏会和 CTA 横向相撞；右上无竞争者。
                PageIndicator(
                    pageCount = items.size,
                    currentPage = pagerState.currentPage,
                    onPageClick = { page ->
                        scope.launch { pagerState.animateScrollToPage(page) }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp),
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
 * 绝对封顶 440：21:9 在 ~1900dp 内容宽的高度。日常只靠它不够——
 * 800 高的窗口里 440dp 仍占 55% 视口（宽屏首屏只剩 banner 的根因），
 * 真正起作用的是下面的视口占比封顶，这里只是超高窗口的 backstop。
 */
private val MAX_BANNER_HEIGHT = 440.dp

/**
 * 视口占比封顶：banner 再高不超窗口高的 40%，宽屏首屏必须露头分类行。
 *
 * 为什么不用固定 dp：同一 440dp 在 800 高窗口占 55%、在 1080 高窗口占 41%——
 * "过大"是相对视口的体感，只能用相对值治。手机（225dp）与平板天然低于此线，
 * 只有宽屏会被它动手。
 */
private const val MAX_BANNER_VIEWPORT_FRACTION = 0.40f

/**
 * Banner 高度纯函数（宽高比档 × 绝对封顶 × 视口占比封顶三取小）。
 *
 * 单独拆出供单测直调：组合内的 `BoxWithConstraints` / `LocalWindowInfo` 不可测，
 * 公式本身必须可断言（见 `BannerHeightTest`）。
 *
 * @param viewportHeight 窗口高；≤ 0（取不到窗口信息时）则退化为前两项取小。
 */
internal fun bannerHeightFor(contentWidth: Dp, viewportHeight: Dp): Dp {
    val aspectHeight = minOf(
        contentWidth * 9f / 16f,
        maxOf(contentWidth * 9f / 21f, MIN_BANNER_HEIGHT),
        MAX_BANNER_HEIGHT,
    )
    if (viewportHeight <= 0.dp) return aspectHeight
    return minOf(aspectHeight, viewportHeight * MAX_BANNER_VIEWPORT_FRACTION)
}
