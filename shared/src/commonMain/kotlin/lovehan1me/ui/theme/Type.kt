package lovehan1me.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
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
 *
 *    ⚠️ **Emphasized 档必须与同名常规档保持一致的行高**。此前只修正了常规档，
 *    `bodyMediumEmphasized` 仍是 20sp —— 同一段混排文本里常规与强调两种行高并存，
 *    换行位置会错位。现在 15 档 Emphasized 中与正文对应的三档同步修正。
 * 2. **强调字阶（Expressive 资产）**：M3E 的强调档字重是**逐档指定**的（已核验
 *    1.12.0-alpha03：body 三档 / titleLarge / headlineSmall 为 w500，titleMedium /
 *    labelLarge 为 w700）—— 逐档各取各的会造出"大标题强调得反而更轻"的反直觉结果。
 *    **15 档全部统一取 Bold**，理由有二：
 *
 *    - **沿用项目既有观感**：迁移前这些位置写的就是 `fontWeight = FontWeight.Bold`，
 *      取 Bold 让整轮迁移成为**视觉零变化**的替换 —— 只换架构，不动外观。
 *    - **避开合成加粗**：各平台 CJK 字体的字重档位不齐（Noto Sans SC 静态版没有
 *      SemiBold 档，微软雅黑档位也不齐），请求 w600 时可能落到**合成加粗**，笔画
 *      边缘发虚。Bold 是各平台 CJK 字体都具备的保底档位，不会触发合成。
 *
 *    M3E 的 Emphasized 规范约束的是"存在这一档、且被系统化使用"，字重具体取 600
 *    还是 700 属于品牌调性范畴，规范未作强制。
 * 3. **不引入自定义字体**：全项目零 `FontFamily` 资源。字体的引入（许可 / 体积 / 三端
 *    加载）是独立决策，字阶不等它。
 *
 * size 一律沿用 M3 基准（display 57/45/36、headline 32/28/24、title 22/16/14、
 * body 16/14/12、label 14/12/11）—— 项目 254 处调用全部建立在这套字号语义上，
 * 改 size 是全局视觉事件，必须带着设计意图单独做，不在这里夹带。
 */
private val Base = Typography()

val AppTypography: Typography = Base.copy(
    // —— CJK 行高修正（常规档）——
    // 正文主力（55 处调用）：14sp / 20 → 22，行高比 1.43 → 1.57。
    bodyMedium = Base.bodyMedium.copy(lineHeight = 22.sp),
    // 副信息（35 处调用）：12sp / 16 → 18，行高比 1.33 → 1.5。
    bodySmall = Base.bodySmall.copy(lineHeight = 18.sp),
    // 正文大档（9 处）：16sp / 24 已是 1.5，保持；显式写出以声明"检查过"。
    bodyLarge = Base.bodyLarge,

    // —— Expressive 强调字阶：15 档全量收敛 SemiBold ——
    // display 三档：项目暂未消费，一并声明以保证"任何强调都同权重"。
    displayLargeEmphasized = Base.displayLargeEmphasized.copy(fontWeight = FontWeight.Bold),
    displayMediumEmphasized = Base.displayMediumEmphasized.copy(fontWeight = FontWeight.Bold),
    displaySmallEmphasized = Base.displaySmallEmphasized.copy(fontWeight = FontWeight.Bold),
    headlineLargeEmphasized = Base.headlineLargeEmphasized.copy(fontWeight = FontWeight.Bold),
    headlineMediumEmphasized = Base.headlineMediumEmphasized.copy(fontWeight = FontWeight.Bold),
    headlineSmallEmphasized = Base.headlineSmallEmphasized.copy(fontWeight = FontWeight.Bold),
    titleLargeEmphasized = Base.titleLargeEmphasized.copy(fontWeight = FontWeight.Bold),
    titleMediumEmphasized = Base.titleMediumEmphasized.copy(fontWeight = FontWeight.Bold),
    titleSmallEmphasized = Base.titleSmallEmphasized.copy(fontWeight = FontWeight.Bold),
    // 正文三档：行高同步跟随同名常规档，避免混排时行高错位。
    bodyLargeEmphasized = Base.bodyLargeEmphasized.copy(
        fontWeight = FontWeight.Bold,
        lineHeight = Base.bodyLarge.lineHeight,
    ),
    bodyMediumEmphasized = Base.bodyMediumEmphasized.copy(
        fontWeight = FontWeight.Bold,
        lineHeight = 22.sp,
    ),
    bodySmallEmphasized = Base.bodySmallEmphasized.copy(
        fontWeight = FontWeight.Bold,
        lineHeight = 18.sp,
    ),
    // label 三档：UI 控件文字，字号小、行盒本就宽松（11/16 ≈ 1.45），不加行高。
    labelLargeEmphasized = Base.labelLargeEmphasized.copy(fontWeight = FontWeight.Bold),
    labelMediumEmphasized = Base.labelMediumEmphasized.copy(fontWeight = FontWeight.Bold),
    labelSmallEmphasized = Base.labelSmallEmphasized.copy(fontWeight = FontWeight.Bold),
)

/**
 * 强调字阶的**语义入口**（M3E Tactic 3：Guide attention with typography）。
 *
 * 存在的理由：业务层直接写 `MaterialTheme.typography.titleSmallEmphasized` 时，
 * "为什么这里要强调""该用哪一档"这两个判断会散落在 254 处调用点里，改策略要动
 * 几十个文件。这里把**判断**收进语义名，业务层只表达意图，不表达档位。
 *
 * 用法：`style = AppEmphasis.cardTitle`（在 `@Composable` 上下文）。
 *
 * **档位映射按"字号不变、字重也不变"的原则选定** —— 迁移前这些位置写的就是
 * 手写 `fontWeight = FontWeight.Bold`，强调档同样取 Bold，因此迁移是**视觉零变化**
 * 的替换：字号不动、字重不动、布局不动，只有"这个强调由谁提供"从散落各处的手写
 * 覆盖换成了集中定义的字阶。这是让全部调用点能安全迁移的前提。
 *
 * **使用契约**（M3E 明确要求 + 本项目场景）：
 * - [dialogTitle] —— 对话框标题。M3E 规范要求 Dialog 标题必须强调。
 * - [heroTitle] —— 页面主标题 / hero 标题（24sp 那一档）。
 * - [pageTitle] —— 页面级 / 大区块标题（22sp 那一档）。
 * - [sectionTitle] —— 分区标题（16sp 那一档）。
 * - [groupTitle] —— 分组 / 列表项标题（16sp 那一档）。
 * - [counterValue] —— 计数（16sp 那一档）。
 * - [cardTitle] —— 视频卡主标题。列表里被扫读的第一信息，也是全项目曝光量最高的文本。
 * - [metricValue] —— 关键数值（连续签到天数、下载进度百分比、计数）。
 * - [emphasizedBody] —— 需要突出的正文片段。
 *
 * **不要**用它去强调：按钮文字（M3 按钮已内置 labelLarge 的强调语义）、
 * 纯装饰性标签、以及任何同一屏里超过两处的地方 —— 强调一旦泛滥就等于没有强调。
 */
object AppEmphasis {
    val dialogTitle: TextStyle
        @Composable get() = MaterialTheme.typography.headlineSmallEmphasized

    /** 页面主标题 / hero 标题（24sp）：艺术家名、预览详情大标题。
     *  与 [dialogTitle] 同档，用途不同故独立命名。 */
    val heroTitle: TextStyle
        @Composable get() = MaterialTheme.typography.headlineSmallEmphasized

    /** 页面级 / 大区块标题（22sp）。项目里分区标题存在 16sp 与 22sp 两档并存的情况
     *  （首页 `CategoryBlock` 用 16sp，签到页用 22sp）—— 本语义承接 22sp 那一档，
     *  迁移期不做统一。两档长期应收敛为一档，那是独立的视觉决策。 */
    val pageTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleLargeEmphasized

    val sectionTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleMediumEmphasized

    /** 分组 / 列表项标题（16sp 那一档）：下载分组名、列表里的视频标题。
     *  与 [sectionTitle] 同档但用途不同，独立命名以便未来分别调整。 */
    val groupTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleMediumEmphasized

    /** 计数与关键数值（16sp 那一档）：选择态 toolbar 的「已选 / 总数」、价格。
     *  [metricValue] 承接 14sp 的卡片内统计值，两者因既存字号差异而分开。 */
    val counterValue: TextStyle
        @Composable get() = MaterialTheme.typography.titleMediumEmphasized

    val cardTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleSmallEmphasized

    /** 列表项主标题（评论用户名、下载项、播放列表条目）。与 [cardTitle] 同档但独立命名，
     *  便于未来单独调整其一而不牵连另一处。 */
    val itemTitle: TextStyle
        @Composable get() = MaterialTheme.typography.titleSmallEmphasized

    val metricValue: TextStyle
        @Composable get() = MaterialTheme.typography.titleSmallEmphasized

    val emphasizedBody: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMediumEmphasized
}
