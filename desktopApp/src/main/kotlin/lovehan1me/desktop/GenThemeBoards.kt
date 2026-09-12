package lovehan1me.desktop

import com.kyant.m3color.dynamiccolor.ColorSpec
import com.kyant.m3color.dynamiccolor.DynamicScheme
import com.kyant.m3color.hct.Hct
import com.kyant.m3color.scheme.SchemeContent
import com.kyant.m3color.scheme.SchemeExpressive
import com.kyant.m3color.scheme.SchemeFidelity
import com.kyant.m3color.scheme.SchemeFruitSalad
import com.kyant.m3color.scheme.SchemeNeutral
import com.kyant.m3color.scheme.SchemeRainbow
import com.kyant.m3color.scheme.SchemeTonalSpot
import com.kyant.m3color.scheme.SchemeVibrant
import java.io.File

/**
 * 主题预生成工具（dev-only，不进正式流程）。
 *
 * 跑法：`HAN1ME_GEN_BOARDS=1 ./gradlew :desktopApp:run`，跑完即退，
 * 输出写到 `shared/.../ui/theme/GeneratedThemeBoards.kt`。
 *
 * 配方必须与 `ui/theme/ThemeBoards.kt` 的 8 槽保持一致（双源是故意的：
 * 生成物才是运行时真相，这里改了就要重跑并提交生成文件）。
 * 8 槽 × 明暗 × 3 对比度 = 48 套 ColorScheme 常量。
 */
fun runGenBoardsIfRequested(): Boolean {
    if (System.getenv("HAN1ME_GEN_BOARDS") != "1") return false
    val roots = listOf(
        File("../shared/src/commonMain/kotlin/lovehan1me/ui/theme/GeneratedThemeBoards.kt"),
        File("shared/src/commonMain/kotlin/lovehan1me/ui/theme/GeneratedThemeBoards.kt"),
    )
    val target = roots.firstOrNull { it.parentFile?.isDirectory == true }
        ?: error("GEN_BOARDS: shared theme dir not found from ${File(".").absolutePath}")
    target.writeText(buildBoardsFile())
    println("GEN_BOARDS: wrote ${target.absolutePath}")
    return true
}

private data class Recipe(
    val id: String,
    val seed: Int,
    val style: String,
    val neutralSurfaces: Boolean,
)

// id/seed/style 必须与 ThemeBoards.kt 一致。
private val RECIPES = listOf(
    Recipe("sakura", 0xFFF596AAL.toInt(), "TonalSpot", neutralSurfaces = true),
    Recipe("take", 0xFF8BC34AL.toInt(), "TonalSpot", neutralSurfaces = true),
    Recipe("sou", 0xFF03A9F4L.toInt(), "TonalSpot", neutralSurfaces = true),
    Recipe("yuzu", 0xFFFFF59DL.toInt(), "TonalSpot", neutralSurfaces = true),
    Recipe("midnight", 0xFF3F4C8CL.toInt(), "TonalSpot", neutralSurfaces = false),
    Recipe("nord", 0xFF88C0D0L.toInt(), "Neutral", neutralSurfaces = false),
    Recipe("mono", 0xFF8E8E93L.toInt(), "Neutral", neutralSurfaces = false),
    // system 槽：Android 取壁纸色现场算；其他平台用粉种子全动态（与 ThemeBoards 回落一致）。
    Recipe("system", 0xFFF596AAL.toInt(), "TonalSpot", neutralSurfaces = false),
)

private val CONTRASTS = listOf(0.0, 0.5, 1.0)

private fun schemeOf(seed: Int, style: String, isDark: Boolean, contrast: Double): DynamicScheme {
    val hct = Hct.fromInt(seed)
    val spec = ColorSpec.SpecVersion.SPEC_2026
    val platform = DynamicScheme.Platform.PHONE
    return when (style) {
        "TonalSpot" -> SchemeTonalSpot(hct, isDark, contrast, spec, platform)
        "Neutral" -> SchemeNeutral(hct, isDark, contrast, spec, platform)
        "Vibrant" -> SchemeVibrant(hct, isDark, contrast, spec, platform)
        "Expressive" -> SchemeExpressive(hct, isDark, contrast, spec, platform)
        "Rainbow" -> SchemeRainbow(hct, isDark, contrast, spec, platform)
        "FruitSalad" -> SchemeFruitSalad(hct, isDark, contrast, spec, platform)
        "Fidelity" -> SchemeFidelity(hct, isDark, contrast, spec, platform)
        else -> SchemeContent(hct, isDark, contrast, spec, platform)
    }
}

// 与 DynamicSchemeProvider 的 48 角色映射同序。
private val ROLES: List<Pair<String, (DynamicScheme) -> Int>> = listOf(
    "primary" to { it.primary }, "onPrimary" to { it.onPrimary },
    "primaryContainer" to { it.primaryContainer }, "onPrimaryContainer" to { it.onPrimaryContainer },
    "inversePrimary" to { it.inversePrimary },
    "secondary" to { it.secondary }, "onSecondary" to { it.onSecondary },
    "secondaryContainer" to { it.secondaryContainer }, "onSecondaryContainer" to { it.onSecondaryContainer },
    "tertiary" to { it.tertiary }, "onTertiary" to { it.onTertiary },
    "tertiaryContainer" to { it.tertiaryContainer }, "onTertiaryContainer" to { it.onTertiaryContainer },
    "background" to { it.background }, "onBackground" to { it.onBackground },
    "surface" to { it.surface }, "onSurface" to { it.onSurface },
    "surfaceVariant" to { it.surfaceVariant }, "onSurfaceVariant" to { it.onSurfaceVariant },
    "surfaceTint" to { it.surfaceTint },
    "inverseSurface" to { it.inverseSurface }, "inverseOnSurface" to { it.inverseOnSurface },
    "error" to { it.error }, "onError" to { it.onError },
    "errorContainer" to { it.errorContainer }, "onErrorContainer" to { it.onErrorContainer },
    "outline" to { it.outline }, "outlineVariant" to { it.outlineVariant },
    "scrim" to { it.scrim },
    "surfaceBright" to { it.surfaceBright }, "surfaceDim" to { it.surfaceDim },
    "surfaceContainer" to { it.surfaceContainer },
    "surfaceContainerHigh" to { it.surfaceContainerHigh },
    "surfaceContainerHighest" to { it.surfaceContainerHighest },
    "surfaceContainerLow" to { it.surfaceContainerLow },
    "surfaceContainerLowest" to { it.surfaceContainerLowest },
    "primaryFixed" to { it.primaryFixed }, "primaryFixedDim" to { it.primaryFixedDim },
    "onPrimaryFixed" to { it.onPrimaryFixed }, "onPrimaryFixedVariant" to { it.onPrimaryFixedVariant },
    "secondaryFixed" to { it.secondaryFixed }, "secondaryFixedDim" to { it.secondaryFixedDim },
    "onSecondaryFixed" to { it.onSecondaryFixed }, "onSecondaryFixedVariant" to { it.onSecondaryFixedVariant },
    "tertiaryFixed" to { it.tertiaryFixed }, "tertiaryFixedDim" to { it.tertiaryFixedDim },
    "onTertiaryFixed" to { it.onTertiaryFixed }, "onTertiaryFixedVariant" to { it.onTertiaryFixedVariant },
)

// B′ 合并：表面家族取 Neutral，强调家族取 accents（与 ThemeBoards 现场版同规则）。
private val ACCENT_ROLES = setOf(
    "primary", "onPrimary", "primaryContainer", "onPrimaryContainer", "inversePrimary",
    "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
    "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
    "primaryFixed", "primaryFixedDim", "onPrimaryFixed", "onPrimaryFixedVariant",
    "secondaryFixed", "secondaryFixedDim", "onSecondaryFixed", "onSecondaryFixedVariant",
    "tertiaryFixed", "tertiaryFixedDim", "onTertiaryFixed", "onTertiaryFixedVariant",
)

private fun hex(argb: Int): String = "Color(0x%08X)".format(argb)

private fun buildBoardsFile(): String {
    val sb = StringBuilder()
    sb.appendLine("package lovehan1me.ui.theme")
    sb.appendLine()
    sb.appendLine("import androidx.compose.material3.ColorScheme")
    sb.appendLine("import androidx.compose.ui.graphics.Color")
    sb.appendLine()
    sb.appendLine("/**")
    sb.appendLine(" * 预生成命名色板（DO NOT EDIT BY HAND）。")
    sb.appendLine(" *")
    sb.appendLine(" * 由 desktopApp 的 GenThemeBoards（HAN1ME_GEN_BOARDS=1）用 m3color SPEC_2026 生成，")
    sb.appendLine(" * 8 槽 × 明暗 × 3 对比度 = 48 套；运行时查表，三端字节级一致。")
    sb.appendLine(" * 配方见 GenThemeBoards.RECIPES（必须与 ThemeBoards.kt 同步）。")
    sb.appendLine(" */")
    sb.appendLine("internal object GeneratedThemeBoards {")
    sb.appendLine()
    sb.appendLine("    fun scheme(boardId: String, isDark: Boolean, contrastSpec: Double): ColorScheme = when {")
    for (r in RECIPES) {
        for (dark in listOf(false, true)) {
            for (c in CONTRASTS) {
                val accents = schemeOf(r.seed, r.style, dark, c)
                val neutrals = if (r.neutralSurfaces) schemeOf(r.seed, "Neutral", dark, c) else null
                sb.appendLine("        boardId == \"${r.id}\" && isDark == $dark && contrastSpec == $c -> ColorScheme(")
                for ((name, get) in ROLES) {
                    val src = if (neutrals != null && name !in ACCENT_ROLES) neutrals else accents
                    sb.appendLine("            $name = ${hex(get(src))},")
                }
                sb.appendLine("        )")
            }
        }
    }
    sb.appendLine("        else -> ColorScheme(")
    for ((name, _) in ROLES) {
        sb.appendLine("            $name = Color(0xFFF596AA),")
    }
    sb.appendLine("        )")
    sb.appendLine("    }")
    sb.appendLine("}")
    return sb.toString()
}
