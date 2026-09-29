package lovehan1me.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle as MaterialKolorPaletteStyle
import com.materialkolor.dynamicColorScheme
import lovehan1me.core.domain.model.ContrastLevel
import lovehan1me.core.domain.model.PaletteStyle

/**
 * 命名主题槽位（12 槽 = 11 命名 + 1 跟随系统）。
 *
 * 每个槽位都是所见即所得的完整配方（种子色 + 调色板风格 + 表面策略），设置页直接渲染落地
 * 效果，不让用户心算"HCT 展开后 primary 长什么样"。槽位顺序按色相环排：从品牌粉 [Sakura]
 * 起顺时针走一圈（粉→红→橙→黄→绿→蓝→紫→品），再接灰阶族与系统槽。
 *
 * 配方只分三族：
 * - 彩色族（[neutralSurfaces] = true）：**表面固定走 TonalSpot**，强调走 [style]，同一种子合并。
 *   - 樱/柿/柚/竹/苍/藤 走 [PaletteStyle.TonalSpot]（平静、成套）；
 *   - 霞 走 [PaletteStyle.Vibrant]（唯一的高彩度档，给想"浓"的人一个选择）；
 *   - 本命 走 [PaletteStyle.Content]（把种子色几乎原样保留进 primaryContainer，品牌忠实）。
 * - 灰阶族（[neutralSurfaces] = false）：整套走 [style]，静态手调感 —— 夜/雾/墨。
 *   注意"墨·纯灰"用的是 [PaletteStyle.Monochrome]（真·黑白灰），不是 [PaletteStyle.Neutral]
 *   （后者定义是"比单色**略微**有色"，名不副实）。
 * - 系统族（[isSystem]）：Android S+ 取壁纸色全动态，其余平台走种子全动态。
 *
 * 配色由 [boardColorScheme] 在 commonMain 用 materialkolor 现场算（真三端同源），
 * 不再查预生成表。
 */
enum class ThemeBoard(
    val id: String,
    /** 中文名单字：樱/本命/柿/柚/竹/苍/藤/霞/夜/雾/墨/随。 */
    val title: String,
    /** 副标：英文彩蛋 + 色相说明。 */
    val subtitle: String,
    val seedArgb: Long,
    val style: PaletteStyle,
    val neutralSurfaces: Boolean,
    val isSystem: Boolean = false,
) {
    Sakura(
        "sakura", "樱", "Momoi · 粉",
        0xFFF596AA,
        PaletteStyle.TonalSpot, neutralSurfaces = true,
    ),
    Honmei(
        "honmei", "本命", "Honmei · 品牌忠实",
        0xFFD32F2F,
        PaletteStyle.Content, neutralSurfaces = true,
    ),
    Kaki(
        "kaki", "柿", "Kaki · 橙",
        0xFFE8834A,
        PaletteStyle.TonalSpot, neutralSurfaces = true,
    ),
    Yuzu(
        "yuzu", "柚", "Yuzu · 黄",
        0xFFFFF59D,
        PaletteStyle.TonalSpot, neutralSurfaces = true,
    ),
    Take(
        "take", "竹", "Midori · 绿",
        0xFF8BC34A,
        PaletteStyle.TonalSpot, neutralSurfaces = true,
    ),
    Sou(
        "sou", "苍", "Arisu · 蓝",
        0xFF03A9F4,
        PaletteStyle.TonalSpot, neutralSurfaces = true,
    ),
    Fuji(
        "fuji", "藤", "Fuji · 紫",
        0xFF7E57C2,
        PaletteStyle.TonalSpot, neutralSurfaces = true,
    ),
    Kasumi(
        "kasumi", "霞", "Kasumi · 高彩度",
        0xFFB5179E,
        PaletteStyle.Vibrant, neutralSurfaces = true,
    ),
    Midnight(
        "midnight", "夜", "Midnight · 深蓝灰",
        0xFF3F4C8C,
        PaletteStyle.TonalSpot, neutralSurfaces = false,
    ),
    Nord(
        "nord", "雾", "Nord · 冷灰蓝",
        0xFF88C0D0,
        PaletteStyle.Neutral, neutralSurfaces = false,
    ),
    Mono(
        "mono", "墨", "Mono · 纯灰",
        0xFF8E8E93,
        PaletteStyle.Monochrome, neutralSurfaces = false,
    ),
    System(
        "system", "随", "System · 跟随系统",
        0xFFF596AA,
        PaletteStyle.TonalSpot, neutralSurfaces = false, isSystem = true,
    ),
    ;

    companion object {
        fun fromId(id: String): ThemeBoard = entries.firstOrNull { it.id == id } ?: Sakura
    }
}

/**
 * 槽位配色入口：真三端现场色算（materialkolor，KMP 原生）。
 *
 * 命名槽在此统一走 materialkolor（KMP 原生）在 commonMain 现场算，三端零差异、可随对比度/深浅
 * 任意组合实时重算。配色算法：单源色 → HCT → 全套角色。
 *
 * - 彩色族（[ThemeBoard.neutralSurfaces] = true）：表面家族固定取 TonalSpot、强调家族保留
 *   [style]，合并自两套 scheme（见 [ColorScheme.withNeutralSurfaces]）；
 * - 灰阶族（[ThemeBoard.neutralSurfaces] = false）：整套走 [style]；
 * - 系统族（[ThemeBoard.isSystem]）：有平台取色（Android S+ 壁纸色）就全动态，否则回落粉种子全动态。
 *
 * @param contrastLevel 对比度 spec（0.0 标准 / 1.0 高对比度），直接喂 materialkolor。
 */
@Composable
fun boardColorScheme(
    board: ThemeBoard,
    isDark: Boolean,
    contrastLevel: Double = ContrastLevel.Standard.spec,
): ColorScheme {
    // 无条件读系统取色：只有系统族用得上，但放在条件分支里会让 composable 调用结构
    // 随 board 变，读一次的开销不值得换这个隐患。
    val system = rememberSystemAccentColorOrNull()
    return remember(board, isDark, contrastLevel, system) {
        boardColorSchemeValue(board, isDark, contrastLevel, system)
    }
}

/**
 * [boardColorScheme] 的**非 `@Composable` 内核**：同一份逻辑，不依赖组合、不依赖平台。
 *
 * 单独拆出来是为了让主题配色能在单测里被直接调用 —— 否则"线上到底算出什么颜色"
 * 只能靠肉眼看截图，改色前后无从量化（`ThemeColorAuditTest` 即消费者）。
 */
internal fun boardColorSchemeValue(
    board: ThemeBoard,
    isDark: Boolean,
    contrastLevel: Double,
    systemSeed: Color?,
): ColorScheme {
    // 系统族：有平台取色（Android S+ 壁纸色）就全动态；否则回落种子全动态。
    if (board.isSystem && systemSeed != null) {
        return dynamicColorScheme(
            seedColor = systemSeed,
            isDark = isDark,
            style = MaterialKolorPaletteStyle.TonalSpot,
            contrastLevel = contrastLevel,
        )
    }
    val seed = Color(board.seedArgb)
    val accentScheme = dynamicColorScheme(
        seedColor = seed,
        isDark = isDark,
        style = board.style.toMaterialKolor(),
        contrastLevel = contrastLevel,
    )
    // 中性（表面）家族固定取 TonalSpot：它的中性调色板色度约 6，是本项目"有颜色但不吵"
    // 的基准。此前取 Neutral（色度约 2），实测把导航栏、卡片、次要文字全洗成近纯灰。
    // style 本身就是 TonalSpot 的槽位无需合并 —— 两套 scheme 逐字段相同，白算一遍。
    if (!board.neutralSurfaces || board.style == PaletteStyle.TonalSpot) return accentScheme
    val neutralScheme = dynamicColorScheme(
        seedColor = seed,
        isDark = isDark,
        style = MaterialKolorPaletteStyle.TonalSpot,
        contrastLevel = contrastLevel,
    )
    return accentScheme.withNeutralSurfaces(neutralScheme)
}

/**
 * 彩色族合并：表面家族取 [neutral]（固定 TonalSpot 中性，色度约 6），强调家族保留 [accent] 的 [style]。
 *
 * 除 primary / secondary / tertiary 及其容器、fixed 变体之外，其余（surface 家族、outline、
 * error）均取自 neutral。页面底色 `surface` 与强调色无关，两套 scheme 本来就同值；
 * 真正被这一层改变的是 `surfaceContainer*`、`onSurfaceVariant`、`outline*` ——
 * 也就是导航栏、卡片、次要文字的染色。
 */
private fun ColorScheme.withNeutralSurfaces(neutral: ColorScheme): ColorScheme = copy(
    background = neutral.background,
    onBackground = neutral.onBackground,
    surface = neutral.surface,
    onSurface = neutral.onSurface,
    surfaceVariant = neutral.surfaceVariant,
    onSurfaceVariant = neutral.onSurfaceVariant,
    surfaceTint = neutral.surfaceTint,
    inverseSurface = neutral.inverseSurface,
    inverseOnSurface = neutral.inverseOnSurface,
    surfaceBright = neutral.surfaceBright,
    surfaceDim = neutral.surfaceDim,
    surfaceContainer = neutral.surfaceContainer,
    surfaceContainerHigh = neutral.surfaceContainerHigh,
    surfaceContainerHighest = neutral.surfaceContainerHighest,
    surfaceContainerLow = neutral.surfaceContainerLow,
    surfaceContainerLowest = neutral.surfaceContainerLowest,
    outline = neutral.outline,
    outlineVariant = neutral.outlineVariant,
    scrim = neutral.scrim,
    error = neutral.error,
    onError = neutral.onError,
    errorContainer = neutral.errorContainer,
    onErrorContainer = neutral.onErrorContainer,
)

private fun PaletteStyle.toMaterialKolor(): MaterialKolorPaletteStyle = when (this) {
    PaletteStyle.TonalSpot -> MaterialKolorPaletteStyle.TonalSpot
    PaletteStyle.Neutral -> MaterialKolorPaletteStyle.Neutral
    PaletteStyle.Monochrome -> MaterialKolorPaletteStyle.Monochrome
    PaletteStyle.Vibrant -> MaterialKolorPaletteStyle.Vibrant
    PaletteStyle.Content -> MaterialKolorPaletteStyle.Content
}

/**
 * AMOLED 纯黑叠加（与深浅正交的独立开关，只在深色下生效）。
 *
 * 纯 `copy` 操作，无平台依赖，三端一致。单独保留：播放器色算（VideoPlayerShell）也复用它，
 * 作为全项目唯一的 AMOLED 实现，播放器与全局主题共用它，避免两处各写一份纯黑表。
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
