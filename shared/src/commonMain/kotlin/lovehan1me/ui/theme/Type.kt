package lovehan1me.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 应用字阶（审计 P0：此前 `Theme.kt` 直接传 `Typography()` 默认值，等于声明"不定制"）。
 *
 * 定制原则 —— **每一处改动都有明确理由，没有理由的不动**：
 *
 * 1. **CJK 行高修正**：M3 基准行高按拉丁字形设计（约 1.2–1.5 倍字号）。中文方块字
 *    占位更高，同样行距下明显拥挤 —— 正文两档（bodyMedium / bodySmall）行高 +2sp。
 *    标题 / label 行高保持：短文本 + 加粗字形的实际行盒更宽松，不必加。
 * 2. **强调字阶（Expressive 资产）**：M3E 的强调档字重是**逐档指定**的（已核验
 *    1.12.0-alpha03：body 三档 / titleLarge / headlineSmall 为 w500，titleMedium /
 *    labelLarge 为 w700）—— w500 对中文与常规档区分度很低，w700 又偏黑、与全局强调
 *    取值不成体系。凡项目**实际消费**的强调档统一收敛到 SemiBold：body 两档 + title
 *    两档 + headlineSmall 一档；新增消费时按同一策略补一行（别照英文习惯直接取 M3E 默认字重）。
 * 3. **不引入自定义字体**：全项目零 `FontFamily` 资源。字体的引入（许可 / 体积 / 三端
 *    加载）是独立决策，字阶不等它。
 *
 * size 一律沿用 M3 基准（display 57/45/36、headline 32/28/24、title 22/16/14、
 * body 16/14/12、label 14/12/11）—— 项目 254 处调用全部建立在这套字号语义上，
 * 改 size 是全局视觉事件，必须带着设计意图单独做，不在这里夹带。
 */
private val Base = Typography()

val AppTypography: Typography = Base.copy(
    // —— CJK 行高修正 ——
    // 正文主力（55 处调用）：14sp / 20 → 22，行高比 1.43 → 1.57。
    bodyMedium = Base.bodyMedium.copy(lineHeight = 22.sp),
    // 副信息（35 处调用）：12sp / 16 → 18，行高比 1.33 → 1.5。
    bodySmall = Base.bodySmall.copy(lineHeight = 18.sp),
    // 正文大档（9 处）：16sp / 24 已是 1.5，保持；显式写出以声明"检查过"。
    bodyLarge = Base.bodyLarge,

    // —— Expressive 强调字阶 ——
    bodyMediumEmphasized = Base.bodyMediumEmphasized.copy(fontWeight = FontWeight.SemiBold),
    bodySmallEmphasized = Base.bodySmallEmphasized.copy(fontWeight = FontWeight.SemiBold),
    titleLargeEmphasized = Base.titleLargeEmphasized.copy(fontWeight = FontWeight.SemiBold),
    titleMediumEmphasized = Base.titleMediumEmphasized.copy(fontWeight = FontWeight.SemiBold),
    headlineSmallEmphasized = Base.headlineSmallEmphasized.copy(fontWeight = FontWeight.SemiBold),
)
