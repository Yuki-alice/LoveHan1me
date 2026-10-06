package lovehan1me.ui.component

import lovehan1me.ui.theme.HanimeDefaults
import lovehan1me.core.util.LogUtil
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import lovehan1me.ui.component.rememberHanimeImageLoader
import androidx.compose.ui.platform.LocalInspectionMode
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import lovehan1me.data.SettingsRepository
import lovehan1me.core.domain.model.AppLanguage
import lovehan1me.Res
import lovehan1me.loading
import lovehan1me.ui.adaptive.columnsForMinItemWidth
import lovehan1me.ui.adaptive.rememberContentWidthDp

@Composable
fun RetryableImage(
    model: Any,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    retryLimit: Int = 1,
    placeholder: Painter,
    error: Painter,
    contentScale: ContentScale? = ContentScale.Fit
) {
    val context = LocalPlatformContext.current
    var retryCount by remember { mutableIntStateOf(0) }

    // B6：ImageRequest **必须 remember**。
    //
    // 它原先直接写在组合体里，于是每次重组都是一个新的实例。AsyncImage 以 model
    // 作为"要不要重启这次加载"的依据，实例一换就把加载从头再来 —— 表现就是卡片
    // 一重组就闪回占位图。快滑时一屏几十张卡在反复重组，图闪正是这么来的（B6 的
    // 验收口径就是"快滑图闪"）。记住之后，只有 URL 真变或重试推进才会重建请求。
    //
    // 顺带说清为什么**不**按原计划写死 `size()`：coil3 的 AsyncImage 用
    // ConstraintsSizeResolver 从布局约束解析解码尺寸，项目自己的预热路径也写着
    // "解码尺寸由展示侧定，预热定死尺寸反而污染内存缓存"。在这里定死尺寸只会把
    // 不同宽度的卡片塞进同一个缓存键。记忆住的请求本身就是稳定缓存键的来源
    // （data + 解析出的尺寸 + 变换），不必再手工指定 memoryCacheKey。
    //
    // 重试不换 URL：此前 `?retry=` 拼法同时击穿内存/磁盘/服务端三级缓存，
    // 且 CDN 会把它当新资源。失败本就无缓存可命中，同 URL 重发即重拉；
    // 显式 bypass 磁盘只为语义明确。retryCount 只做重组触发器。
    val request = remember(context, model, retryCount) {
        ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .apply { if (retryCount > 0) networkCachePolicy(CachePolicy.DISABLED) }
            .listener(
                onError = { _, result ->
                    LogUtil.e("CoilError", "Image load failed", result.throwable)
                }
            ).build()
    }

    AsyncImage(
        model = request,
        imageLoader = rememberHanimeImageLoader(),
        contentDescription = contentDescription,
        placeholder = placeholder,
        error = error,
        modifier = modifier,
        onError = {
            if (retryCount < retryLimit) retryCount++
        },
        contentScale = contentScale ?: ContentScale.Fit
    )
}

@Composable
fun getColumnCount(itemWidth: Int): Int =
    columnsForMinItemWidth(rememberContentWidthDp(), itemWidth.dp)

@Composable
fun rememberCardResponsiveWidth(
    horizontalPadding: Dp = HanimeDefaults.Spacing.extraLarge,
    itemSpacing: Dp = HanimeDefaults.Spacing.medium
): Pair<Dp, Float> {
    // 内容区可用宽度（常驻抽屉 / 侧栏占宽已扣除），而非整窗宽度。
    val currentWidthDp = rememberContentWidthDp()

    // 横向可见卡片数不再可配：按 160dp 估算卡宽自动推导（预览与运行时同一公式）。
    val estimatedCardWidth = 160.dp
    val itemsToShow =
        maxOf(1f, ((currentWidthDp - (horizontalPadding * 2)) / (estimatedCardWidth + itemSpacing)))

    val safeItemsToShow = maxOf(1f, itemsToShow)
    val cardWidth = (currentWidthDp - (horizontalPadding * 2) - (itemSpacing * (safeItemsToShow - 1))) / safeItemsToShow

    return Pair(cardWidth, safeItemsToShow)
}

/**
 * 视频网格列数。
 *
 * 由宽度自适应决定——**不再被「平板模式」开关门控**。桌面/平板窗口宽度随时变化，
 * 要求用户手动打开开关才会多列属于响应式错位（此前桌面把窗口拉宽也仍是 2 列）。
 * 档位取自设置里的四档配置，阈值统一在 `ui/adaptive/WindowSize` 定义；
 * 宽度取内容区可用宽度，常驻抽屉占宽已扣除。
 */
@Composable
fun rememberVideoGridColumns(): Int {
    // 预览环境拿不到真实窗口宽度，代入常见手机竖屏宽度（→ compact → 2 列）。
    val widthDp = if (LocalInspectionMode.current) 411.dp else rememberContentWidthDp()
    return SettingsRepository.searchGridColumnsConfig.columnsForWidthDp(widthDp.value.toInt())
}

@Composable
fun rememberRandomLoadingHint(): String {
    val defaultHint = stringResource(Res.string.loading)
    if (!SettingsRepository.funLoadingHints) return defaultHint

    val placeholders = loadingHints(SettingsRepository.current.appLanguage)
    return remember(placeholders) { placeholders.random() }
}
