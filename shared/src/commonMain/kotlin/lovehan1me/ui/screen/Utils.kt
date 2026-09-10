package lovehan1me.ui.screen

import lovehan1me.core.util.LogUtil
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import coil3.request.ImageRequest
import coil3.request.crossfade
import lovehan1me.logic.SettingsRepository
import lovehan1me.core.domain.model.AppLanguage
import lovehan1me.Res
import lovehan1me.loading
import lovehan1me.ui.adaptive.columnsForMinItemWidth
import lovehan1me.ui.adaptive.rememberContentWidthDp
import lovehan1me.ui.theme.SpacingLarge
import lovehan1me.ui.theme.SpacingNormal

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
    var currentModel by remember { mutableStateOf(model) }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(currentModel)
            .crossfade(true)
            .listener(
                onError = { _, result ->
                    LogUtil.e("CoilError", "Image load failed", result.throwable)
                }
            ).build(),
        imageLoader = rememberHanimeImageLoader(),
        contentDescription = contentDescription,
        placeholder = placeholder,
        error = error,
        modifier = modifier,
        onError = {
            if (retryCount < retryLimit) {
                retryCount++
                currentModel = "$model?retry=$retryCount"
            }
        },
        contentScale = contentScale ?: ContentScale.Fit
    )
}

@Composable
fun getColumnCount(itemWidth: Int): Int =
    columnsForMinItemWidth(rememberContentWidthDp(), itemWidth.dp)

@Composable
fun rememberCardResponsiveWidth(
    horizontalPadding: Dp = SpacingLarge,
    itemSpacing: Dp = SpacingNormal
): Pair<Dp, Float> {
    // 内容区可用宽度（常驻抽屉 / 侧栏占宽已扣除），而非整窗宽度。
    val currentWidthDp = rememberContentWidthDp()

    val isPreview = LocalInspectionMode.current
    val itemsToShow = if (!isPreview) {
        SettingsRepository.horizontalCardCountConfig.countForWidthDp(currentWidthDp.value.toInt())
    } else {
        val estimatedCardWidth = 160.dp
        maxOf(1f, ((currentWidthDp - (horizontalPadding * 2)) / (estimatedCardWidth + itemSpacing)))
    }

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
