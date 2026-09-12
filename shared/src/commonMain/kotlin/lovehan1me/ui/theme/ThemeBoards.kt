package lovehan1me.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import lovehan1me.core.domain.model.ContrastLevel
import lovehan1me.core.domain.model.PaletteStyle

/**
 * 命名主题槽位（8 槽 = 7 命名 + 1 跟随系统）。
 *
 * 取代旧的「4 种子 × 8 变体 = 32 种玄学组合」：每个槽位都是所见即所得的完整配方，
 * 设置页直接渲染落地效果，不再让用户心算"HCT 展开后 primary 长什么样"。
 *
 * - B′（[neutralSurfaces] = true）：表面走 Neutral（近灰不染色），强调走 [style]，
 *   同一种子合并。樱/竹/苍/柚四槽，解决"绿种子染满屏"事故；
 * - C′（[neutralSurfaces] = false）：整套走 [style]。夜/雾/墨三槽，静态手调感；
 * - A′（[isSystem]）：Android S+ 取壁纸色全动态，其余平台走种子全动态
 *   （给想体验"浓郁"的人留的门）。
 *
 * P2 会把这张表的 8×明暗×3对比度 = 48 套结果预生成进常量表，
 * [boardColorScheme] 的签名不变，内部从"现场算"换成"查表"。
 */
enum class ThemeBoard(
    val id: String,
    /** 中文名单字：樱/竹/苍/柚/夜/雾/墨/随。 */
    val title: String,
    /** 副标：英文彩蛋 + 色相说明。 */
    val subtitle: String,
    val seedArgb: Long,
    /** 回退用副色（无平台色算时顶 secondary / tertiary，保证切换可见）。 */
    val secondArgb: Long,
    val thirdArgb: Long,
    val style: PaletteStyle,
    val neutralSurfaces: Boolean,
    val isSystem: Boolean = false,
) {
    Sakura(
        "sakura", "樱", "Momoi · 粉",
        0xFFF596AA, 0xFFFFB1BF, 0xFFE46988,
        PaletteStyle.TonalSpot, neutralSurfaces = true,
    ),
    Take(
        "take", "竹", "Midori · 绿",
        0xFF8BC34A, 0xFF9FD75C, 0xFF6A9F2B,
        PaletteStyle.TonalSpot, neutralSurfaces = true,
    ),
    Sou(
        "sou", "苍", "Arisu · 蓝",
        0xFF03A9F4, 0xFF8ECDFF, 0xFF0099DD,
        PaletteStyle.TonalSpot, neutralSurfaces = true,
    ),
    Yuzu(
        "yuzu", "柚", "Yuzu · 黄",
        0xFFFFF59D, 0xFFD7CA2C, 0xFF9E9401,
        PaletteStyle.TonalSpot, neutralSurfaces = true,
    ),
    Midnight(
        "midnight", "夜", "Midnight · 深蓝灰",
        0xFF3F4C8C, 0xFF3F4C8C, 0xFF3F4C8C,
        PaletteStyle.TonalSpot, neutralSurfaces = false,
    ),
    Nord(
        "nord", "雾", "Nord · 冷灰蓝",
        0xFF88C0D0, 0xFF81A1C1, 0xFF5E81AC,
        PaletteStyle.Neutral, neutralSurfaces = false,
    ),
    Mono(
        "mono", "墨", "Mono · 纯灰",
        0xFF8E8E93, 0xFF8E8E93, 0xFF8E8E93,
        PaletteStyle.Neutral, neutralSurfaces = false,
    ),
    System(
        "system", "随", "System · 跟随系统",
        0xFFF596AA, 0xFFFFB1BF, 0xFFE46988,
        PaletteStyle.TonalSpot, neutralSurfaces = false, isSystem = true,
    ),
    ;

    companion object {
        fun fromId(id: String): ThemeBoard = entries.firstOrNull { it.id == id } ?: Sakura
    }
}

/**
 * 槽位配色入口（P1：现场算；P2：同签名查预生成表）。
 *
 * @param contrastLevel 对比度 spec（0.0 / 0.5 / 1.0），直接喂 scheme 构造。
 */
/**
 * 槽位配色入口（P2：命名槽查预生成表；跟随系统槽 Android 现场算）。
 *
 * @param contrastLevel 对比度 spec（0.0 / 0.5 / 1.0），直接喂 scheme 构造。
 */
@Composable
fun boardColorScheme(
    board: ThemeBoard,
    isDark: Boolean,
    contrastLevel: Double = ContrastLevel.Standard.spec,
): ColorScheme {
    if (board.isSystem) {
        // A′：有系统取色就全动态（Android S+），否则回落预生成的粉种子全动态。
        val system = rememberSystemAccentColorOrNull()
        if (system != null) {
            provideDynamicColorScheme(
                keyColorArgb = system.toArgb(),
                isDark = isDark,
                style = PaletteStyle.TonalSpot,
                contrastLevel = contrastLevel,
            )?.let { return it }
        }
    }
    // 命名槽 + 系统回落：一律查预生成表（GenThemeBoards，48 套），三端字节级一致。
    return GeneratedThemeBoards.scheme(
        boardId = board.id,
        isDark = isDark,
        contrastSpec = contrastLevel,
    )
}

/**
 * AMOLED 纯黑叠加（Mihon 模式：与深浅无关的正交开关，只在深色下生效）。
 *
 * 纯 `copy` 操作，无平台依赖，三端一致：表面压纯黑系、文字提纯白。
 */
fun ColorScheme.amoled(): ColorScheme = copy(
    surface = Color.Black,
    background = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0C0C0C),
    surfaceContainer = Color(0xFF131313),
    surfaceContainerHigh = Color(0xFF1B1B1B),
    surfaceContainerHighest = Color(0xFF242424),
    surfaceVariant = Color(0xFF242424),
    onSurface = Color.White,
    onBackground = Color.White,
)
