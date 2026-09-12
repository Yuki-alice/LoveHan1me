package lovehan1me.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 页面级排版度量：左右边距 + 首页分类区块的响应式矩阵档位。
 *
 * **为什么边距与矩阵要分开算**：边距是「窗口级」的设计决策（同一台设备固定），
 * 矩阵是「内容级」的决策（受导航 chrome 占宽影响）。导航 Rail 会吃掉 80–220dp，
 * 若直接用窗口宽度分档，1024dp 窗口 + 220dp Rail 只剩 804dp 却按 Expanded 排 4 列，
 * 卡片会被压扁。因此矩阵一律读 **扣掉边距后的可用内容宽度**。
 *
 * 分档阈值由「最小卡宽 170dp」反推：2 列需 ~354dp、3 列需 ~534dp、4 列需 ~714dp、
 * 5 列需 ~894dp，加上边距得到 540 / 760 / 1050 三个分界。
 */
object PageMetrics {

    /**
     * 页面左右外边距。
     *
     * 窄屏贴边一点换取内容宽度，宽屏加大留白避免「内容贴着窗口边缘」。
     */
    fun marginFor(sizeClass: WindowWidthSizeClass): Dp = when (sizeClass) {
        WindowWidthSizeClass.Compact -> 16.dp
        WindowWidthSizeClass.Medium -> 24.dp
        WindowWidthSizeClass.Expanded -> 24.dp
        WindowWidthSizeClass.Large -> 32.dp
        WindowWidthSizeClass.ExtraLarge -> 40.dp
    }

    /** 矩阵分档的可用宽度下界（单位 dp），与 [matrixFor] 的分支一一对应。 */
    const val MATRIX_3_COL_MIN_WIDTH = 540
    const val MATRIX_4_COL_MIN_WIDTH = 760
    const val MATRIX_5_COL_MIN_WIDTH = 1050

    /**
     * 视频详情页「接下来播放」侧栏宽度。
     *
     * **固定像素，不用百分比。** 原来写 `maxWidth * 0.38f`：1920dp 窗口下侧栏会被拉到
     * 730dp，注意力全被相关推荐抢走；对齐 YouTube 的做法（相关推荐栏
     * `min-width:300 / max-width:420`）取固定值。内容区偏窄时降到 320dp，
     * 免得主栏（播放器 + 标题）被压得比侧栏还小。
     */
    val RELATED_PANE_WIDTH = 360.dp
    val RELATED_PANE_WIDTH_NARROW = 320.dp
    /** 低于此内容宽度时侧栏用窄档。 */
    val RELATED_PANE_NARROW_MAX_WIDTH = 1000.dp

    fun relatedPaneWidthFor(contentWidth: Dp): Dp =
        if (contentWidth < RELATED_PANE_NARROW_MAX_WIDTH) {
            RELATED_PANE_WIDTH_NARROW
        } else {
            RELATED_PANE_WIDTH
        }

    /**
     * 视频卡最小宽度分档（审计 P2：原先 `145.dp` / `95.dp` 写死，与响应式体系脱钩）。
     *
     * **为什么只放大最小宽度、不直接定列数**：列数一律交给 `GridCells.Adaptive` 按实际
     * 可用宽度算（它本身即响应式）。写死最小宽度的后果是「宽屏上还是手机尺寸的小卡，
     * 只是排得更多」—— 1920dp 桌面上会排出 11 列 145dp 的卡片，字号 10–12sp 挤在
     * 小卡里，留白全浪费了。分档放大后列数自然变少、卡片变大。
     *
     * 手机档（<600dp）保持原值，视觉零变化；上探档位只影响平板/桌面。
     * 阈值沿用全项目统一的内容宽度断点 600 / 840 / 1200。
     */
    private val NormalCardMinWidths = listOf(145.dp, 160.dp, 180.dp, 200.dp)
    private val SimplifiedCardMinWidths = listOf(95.dp, 110.dp, 125.dp, 140.dp)

    fun videoCardMinWidthFor(availableWidth: Dp, simplified: Boolean): Dp {
        val tier = when {
            availableWidth.value < 600 -> 0
            availableWidth.value < 840 -> 1
            availableWidth.value < 1200 -> 2
            else -> 3
        }
        return if (simplified) SimplifiedCardMinWidths[tier] else NormalCardMinWidths[tier]
    }

    /**
     * 按可用内容宽度（**已扣除左右边距**）取首页分类区块的矩阵档位。
     *
     * 验证过的常见设备（对照《导航重构与设计原型方案》§1.5）：
     * - 手机竖屏 393 → 361 → 2×4
     * - 竖屏平板 768（折叠 Rail 80）→ 656 → 3×3
     * - 平板横屏 1024（折叠 Rail 80）→ 944 → 4×2
     * - 笔电 1440（展开 Rail 220）→ 1220 → 5×2
     * - 桌面 1920（展开 Rail 220）→ 1700 → 5×2
     */
    fun matrixFor(availableWidth: Dp): CategoryMatrix = when {
        availableWidth.value < MATRIX_3_COL_MIN_WIDTH -> CategoryMatrix(2, 4, 12.dp)
        availableWidth.value < MATRIX_4_COL_MIN_WIDTH -> CategoryMatrix(3, 3, 16.dp)
        availableWidth.value < MATRIX_5_COL_MIN_WIDTH -> CategoryMatrix(4, 2, 16.dp)
        else -> CategoryMatrix(5, 2, 20.dp)
    }
}

/**
 * 首页分类区块的矩阵档位。
 *
 * @property columns 列数；@property rows 行数；@property spacing 卡片间距（行列同值）
 */
data class CategoryMatrix(
    val columns: Int,
    val rows: Int,
    val spacing: Dp,
) {
    /** 该档位最多能放下的卡片数，超出部分收进「更多」。 */
    val capacity: Int get() = columns * rows
}

/** 页面左右外边距（窗口档驱动）。 */
@Composable
fun rememberPageHorizontalMargin(): Dp =
    PageMetrics.marginFor(rememberWindowWidthSizeClass())

/**
 * 首页分类区块的矩阵档位（可用内容宽度驱动）。
 *
 * 消费方不要再自行读窗口宽度分档 —— 那会在宽屏 + 展开 Rail 的组合下误判。
 */
@Composable
fun rememberCategoryMatrix(): CategoryMatrix {
    val margin = rememberPageHorizontalMargin()
    val available = (rememberContentWidthDp() - margin * 2).coerceAtLeast(0.dp)
    return PageMetrics.matrixFor(available)
}

/** 视频详情页侧栏宽度（内容宽度驱动，固定像素而非百分比）。 */
@Composable
fun rememberRelatedPaneWidth(): Dp =
    PageMetrics.relatedPaneWidthFor(rememberContentWidthDp())

/**
 * 视频卡最小宽度（内容宽度驱动）。
 *
 * 用于没有局部可用宽度可用的场景（整页网格）；容器内已有 `BoxWithConstraints`
 * 时请直接调 [PageMetrics.videoCardMinWidthFor]，用局部实宽更准 ——
 * 例如底部弹窗的宽度并不等于窗口内容宽度。
 */
@Composable
fun rememberVideoCardMinWidth(simplified: Boolean): Dp {
    val margin = rememberPageHorizontalMargin()
    val available = (rememberContentWidthDp() - margin * 2).coerceAtLeast(0.dp)
    return PageMetrics.videoCardMinWidthFor(available, simplified)
}

/** 整窗高度（dp）。播放器高度的「可用高度」上限用得到。 */
@Composable
fun rememberWindowHeightDp(): Dp = with(LocalDensity.current) {
    LocalWindowInfo.current.containerSize.height.toDp()
}
