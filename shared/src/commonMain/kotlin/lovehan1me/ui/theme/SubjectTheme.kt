package lovehan1me.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import lovehan1me.data.SettingsRepository
import lovehan1me.ui.component.rememberHanimeImageLoader
import kotlin.math.abs

/**
 * 页面级「从封面取色」主题：把当前页面的配色换成由一张封面图的主色现场生成的一套。
 *
 * 与命名主题槽位（[ThemeBoard]）的关系：**槽位是全局偏好，这里是页面级临时换肤**。
 * 只在 [enabled] 为真且成功取到种子色时才接管，否则原样透传 [content]，
 * 所以关掉它等于零行为变化（不会给老用户带来任何视觉差异）。
 *
 * 取色是异步的（要下载 + 解码封面），在种子色就绪前先按外层主题渲染，就绪后再平滑过渡
 * （复用 [animateColorScheme]，与全局换槽位同一条动画路径）。
 *
 * 失败一律**静默回退**到外层主题：封面下载失败、解码失败、图里没有可用的彩色像素，
 * 都只会让这个页面保持原样，不弹错、不白屏。
 *
 * @param coverUrl 封面图地址；空或 null 时不做任何事。
 * @param enabled 用户开关（见 `AppSettings.dynamicSubjectTheme`），默认关。
 */
@Composable
fun SubjectThemeOverride(
    coverUrl: String?,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val seed = if (enabled) rememberCoverSeedColor(coverUrl) else null
    if (seed == null) {
        content()
        return
    }
    // B4：与 HanimeTheme 同源，只订阅主题四量。
    val themeConfig by SettingsRepository.themeConfigFlow.collectAsStateWithLifecycle()
    val isDark = when (themeConfig.themeMode.value) {
        "always_on" -> true
        "always_off" -> false
        else -> isSystemInDarkTheme()
    }
    val baseColorScheme = remember(seed, isDark, themeConfig.contrastLevel) {
        dynamicColorScheme(
            seedColor = seed,
            isDark = isDark,
            style = PaletteStyle.TonalSpot,
            contrastLevel = themeConfig.contrastLevel.spec,
        )
    }
    // AMOLED 与全局主题同规则：只在深色下叠加纯黑。
    //
    // 与 HanimeTheme 同理：amoled() 的 copy() 不加记忆的话，取色主题过渡的每一帧都会
    // 新造一个 ColorScheme 实例，下游按引用比全都跳过不了。记住后过渡期间实例恒定。
    val colorScheme = remember(baseColorScheme, themeConfig.amoled, isDark) {
        if (themeConfig.amoled && isDark) baseColorScheme.amoled() else baseColorScheme
    }
    HanimeTheme(colorScheme = animateColorScheme(colorScheme), content = content)
}

/**
 * 从 [url] 取一个"可做主题种子"的颜色，取不到返回 null。
 *
 * 走的图片管线与列表封面同一条（[rememberHanimeImageLoader]，含代理 / ECH 改写），
 * 所以它和页面上显示的那张封面走的是同一个出口，不会出现"封面能显示但取色请求失败"。
 */
@Composable
private fun rememberCoverSeedColor(url: String?): Color? {
    val context = LocalPlatformContext.current
    val loader = rememberHanimeImageLoader()
    var seed by remember(url) { mutableStateOf<Color?>(null) }
    LaunchedEffect(url, loader) {
        seed = null
        if (url.isNullOrBlank()) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(url)
            // 只要一个主色，不需要原图：交给 Coil 在解码阶段就降到小图，省内存与时间。
            .size(COVER_SAMPLE_PX)
            .build()
        val image = (loader.execute(request) as? SuccessResult)?.image
        seed = image?.toImageBitmapOrNull()?.extractSeedColor()
    }
    return seed
}

/**
 * 平台相关：把 Coil 解出的图变成 Compose 的 [ImageBitmap]（三端 API 不同，见各 actual）。
 *
 * 之所以不直接用 `coil3.Image.draw(Canvas)`：它的 `Canvas` 参数在 Android 是
 * `android.graphics.Canvas`、在其余端是 `org.jetbrains.skia.Canvas`，不是公共类型。
 */
internal expect fun coil3.Image.toImageBitmapOrNull(): ImageBitmap?

/**
 * 从一张（已经降过采样的）图里挑一个主题种子色。
 *
 * 算法：像素转 HSV，**丢掉近黑、近白、近灰的像素**，剩下的按 `饱和度 × 明度` 加权投票到
 * 色相直方图，取权重最大的色相桶；最后把这个色相重建成一个固定 S/V 的种子色。
 *
 * 为什么不直接对全图求平均：平均会把封面里的大片黑边 / 白底也算进去，得到一个发灰的
 * "脏"色；而 materialkolor 拿到脏种子会生成一套没有性格的配色。锁定 S/V 还能保证
 * 无论封面明暗，产出的主题都在同一个"浓度"上，切换影片时观感稳定。
 *
 * @return 无可用彩色像素（纯黑白封面）时返回 null。
 */
private fun ImageBitmap.extractSeedColor(): Color? {
    if (width <= 0 || height <= 0) return null
    val pixels = toPixelMap()
    val bins = FloatArray(HUE_BINS)
    var any = false
    for (y in 0 until height) {
        for (x in 0 until width) {
            val c = pixels[x, y]
            if (c.alpha < 0.4f) continue
            val maxC = maxOf(c.red, c.green, c.blue)
            val minC = minOf(c.red, c.green, c.blue)
            val delta = maxC - minC
            // 近黑（明度太低）与近灰（彩度太低，含白底/黑边）都不投。
            if (maxC < MIN_VALUE || delta <= 0f) continue
            val saturation = delta / maxC
            if (saturation < MIN_SATURATION) continue
            val rawHue = when {
                maxC == c.red -> 60f * ((c.green - c.blue) / delta).mod(6f)
                maxC == c.green -> 60f * (((c.blue - c.red) / delta) + 2f)
                else -> 60f * (((c.red - c.green) / delta) + 4f)
            }
            val hue = if (rawHue < 0f) rawHue + 360f else rawHue
            val bin = (hue / (360f / HUE_BINS)).toInt().coerceIn(0, HUE_BINS - 1)
            bins[bin] += saturation * maxC
            any = true
        }
    }
    if (!any) return null
    var best = 0
    for (i in bins.indices) if (bins[i] > bins[best]) best = i
    val hue = (best + 0.5f) * (360f / HUE_BINS)
    return hsvToColor(hue, SEED_SATURATION, SEED_VALUE)
}

/**
 * HSV → sRGB 的本地实现。
 *
 * 不用 `Color.hsv(...)`：它是 `androidx.compose.ui.graphics` 包里的顶层扩展，
 * 用点号调用还得单独 import 那个函数名；这里只有一个调用点，自带转换更省事也更好核。
 */
private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val h = (hue / 60f).mod(6f)
    val c = value * saturation
    val x = c * (1f - abs((h % 2f) - 1f))
    val m = value - c
    val (r, g, b) = when (h.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

/** 取色采样边长上限（像素）：只要一个主色，64 足够，且三端解码都接近瞬时。 */
private const val COVER_SAMPLE_PX = 64

/** 色相直方图桶数：24 桶 = 每桶 15°，分辨率足以区分相邻色相。 */
private const val HUE_BINS = 24

/** 投票时丢弃的彩度下限（HSV 的 S），低于它算"灰"。 */
private const val MIN_SATURATION = 0.18f

/** 投票时丢弃的明度下限（HSV 的 V），低于它算"黑"。 */
private const val MIN_VALUE = 0.12f

/** 重建种子色用的彩度：中等偏鲜，保证生成的主题有色但不过冲。 */
private const val SEED_SATURATION = 0.55f

/** 重建种子色用的明度：中等，深浅两套配色都能正常展开。 */
private const val SEED_VALUE = 0.6f
