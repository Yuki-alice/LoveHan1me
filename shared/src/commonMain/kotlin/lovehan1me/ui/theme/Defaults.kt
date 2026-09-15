package lovehan1me.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object HanimeDefaults {
    /**
     * 间距阶梯 —— **全项目唯一真源**（M3 8dp 基准：4 / 8 / 12 / 16 / 24 / 32）。
     *
     * 此前存在第二套同名常量（`Dimens.kt` 的顶层 `SpacingNormal` / `SpacingLarge` …），
     * 且 `SpacingMedium` 在两处分别是 8 与 16 —— 这类冲突不报错，只会让「改一处全局
     * 生效」静默失效。现已收敛到此处，替换时值一一对应、**视觉零变化**。
     *
     * 大间距（24 / 32）此前缺失，宽屏留白只能写裸值，一并补齐。
     */
    object Spacing {
        val extraSmall = 2.dp
        val small = 4.dp
        val medium = 8.dp
        val large = 12.dp
        val extraLarge = 16.dp
        /** 24dp：分区之间的大留白。 */
        val extraExtraLarge = 24.dp
        /** 32dp：页面级大留白（宽屏分组间距）。 */
        val huge = 32.dp
        val itemHorizontal = extraExtraLarge
        val itemVertical = extraLarge
        val contentHorizontal = extraLarge
        val contentVertical = medium
    }

    /**
     * 宽屏限宽（响应式设计的一部分）。
     *
     * 三端共用一份 UI，桌面全屏可达 2000dp+：文本密集型页面若让行宽拉满，可读性会明显
     * 下降。上限大于可用宽度时不产生任何影响，故窄屏天然安全。
     *
     * **两个档位的依据**：MD3 给出的可读性红线是「正文保持 40–60 字符/行」。按 16sp 正文
     * （拉丁文均宽约 0.53em ≈ 8.5dp）估算，40 字符 ≈ 340dp、60 字符 ≈ 510dp。
     * 此前只有一个 `contentMax = 840dp`（≈98 字符/行），明显超线，且只用在设置页；
     * 其余页面（首页、搜索、视频简介、评论）完全没有限宽 —— 桌面全屏下行宽可到 1800dp+。
     *
     * 这里不再追求「刚好 60 字符」：只有两段式行结构（title + summary）的表单，
     * 收到 640dp 会影响信息密度；纯阅读内容再宽一档。两者都远好于原来的无限制。
     */
    /**
     * M3 官方**控件尺寸档**（icon button / 同类方形控件的**容器视觉尺寸**）：XS 32 / S 40 / M 56。
     *
     * 与 [Spacing] 是两回事：Spacing 是留白阶梯，这里是控件自身的档位。
     * 也**不等于触控目标** —— 触控目标恒 ≥48dp（M3 硬指标），由命中区放大处理，
     * 见 `VideoPlayerUi` 的 `playerHitTarget`：视觉走这里的档位，命中区另行补到 48dp。
     *
     * 替换原则与 [Spacing] 相同：**值一一对应**，换 token 不改像素。
     */
    object Sizes {
        /** XS：32dp —— 顶栏/浮层里的紧凑图标按钮。 */
        val controlXS = 32.dp
        /** S：40dp —— M3 icon button 的默认档。 */
        val controlS = 40.dp
        /** M：56dp —— 需要强调的主控按钮（如浮动操作键）。 */
        val controlM = 56.dp
    }

    /**
     * 播放器**叠层透明度阶梯** —— 播放器里所有"白/黑的第 N 档"都只从这里取。
     *
     * 背景：播放器一度有 25 处裸 `alpha = 0.xx`（按千行密度约是 animeko 播放器模块的 18 倍），
     * 导致"把控制栏压暗一点"这种改动要满文件找同类值。这里按**语义角色**归并，
     * 值与原硬编码**逐一对应**（只改名与位置，不改值）。
     */
    object OverlayAlpha {
        /** 玻璃底（chip / 药丸按钮）。 */
        const val glass = 0.08f
        /** 描边（1dp 白描边）。 */
        const val border = 0.06f
        /** 分隔线 / 滑块轨道的浅描边。 */
        const val divider = 0.12f
        /** 进度轨道底。 */
        const val track = 0.14f
        /** 已缓冲进度。 */
        const val trackBuffered = 0.32f
        /** thumb 外圈光晕。 */
        const val thumbGlow = 0.22f
        /** 顶栏 scrim 起点（渐变最深端）。 */
        const val scrim = 0.75f
        /** 底栏 scrim 起点（比顶栏更深一档）。 */
        const val scrimDeep = 0.82f
        /** 底部控制栏的玻璃底（作用在模糊层之上）。 */
        const val barSurface = 0.18f
        /**
         * 进度条时间预览气泡的底。
         *
         * 比 [barSurface] 实得多：气泡是**压在画面上**的独立浮层，不像控制栏那样有模糊底兜着，
         * 0.18 的玻璃在亮画面上读不出白字。
         */
        const val previewBubble = 0.82f
        /** 侧栏遮罩。 */
        const val panelDim = 0.72f
        /** 模糊压暗（posterBlur 之上的那层黑）。 */
        const val blurDim = 0.32f
        /** 画面压暗（非 scrim 的整屏压暗）。 */
        const val videoDim = 0.46f
        /** 锁定按钮底。 */
        const val lockButton = 0.45f
        /** 正文极淡（水印级）。 */
        const val textFaint = 0.04f
        /** 三级文字（时间/次要信息）。 */
        const val textTertiary = 0.72f
        /** 二级文字（正文说明）。 */
        const val textSecondary = 0.88f
        /** 一级半文字（强调正文）。 */
        const val textStrong = 0.92f
        /** 标题文字。 */
        const val textPrimary = 0.95f
    }

    /**
     * 播放器**叠层颜色** —— scrim 渐变档 / 玻璃底 / 描边 / 图标。
     * 全部由 [OverlayAlpha] 合成，改透明度只动一处。
     */
    object Overlay {
        /** 叠层上的图标/文字基色（纯白）。 */
        val onScrim = Color.White
        /** 画面底（无视频时的黑底）。 */
        val backdrop = Color.Black

        val glass = Color.White.copy(alpha = OverlayAlpha.glass)
        val border = Color.White.copy(alpha = OverlayAlpha.border)
        val divider = Color.White.copy(alpha = OverlayAlpha.divider)
        val track = Color.White.copy(alpha = OverlayAlpha.track)
        val trackBuffered = Color.White.copy(alpha = OverlayAlpha.trackBuffered)
        val thumbGlow = Color.White.copy(alpha = OverlayAlpha.thumbGlow)

        /** 进度条时间预览气泡的底（深色胶囊，压在画面上保证白字可读）。 */
        val previewBubble = Color.Black.copy(alpha = OverlayAlpha.previewBubble)

        /** 顶栏 scrim 渐变的两端（透明 → [OverlayAlpha.scrim]）。 */
        val scrimTopStart = Color.Transparent
        val scrimTopEnd = Color.Black.copy(alpha = OverlayAlpha.scrim)
        /** 底栏 scrim 渐变的两端。 */
        val scrimBottomStart = Color.Transparent
        val scrimBottomEnd = Color.Black.copy(alpha = OverlayAlpha.scrimDeep)

        val barSurface = Color.Black.copy(alpha = OverlayAlpha.barSurface)
        val panelDim = Color.Black.copy(alpha = OverlayAlpha.panelDim)
        val blurDim = Color.Black.copy(alpha = OverlayAlpha.blurDim)
        val videoDim = Color.Black.copy(alpha = OverlayAlpha.videoDim)
        val lockButton = Color.Black.copy(alpha = OverlayAlpha.lockButton)

        val textFaint = Color.White.copy(alpha = OverlayAlpha.textFaint)
        val textTertiary = Color.White.copy(alpha = OverlayAlpha.textTertiary)
        val textSecondary = Color.White.copy(alpha = OverlayAlpha.textSecondary)
        val textStrong = Color.White.copy(alpha = OverlayAlpha.textStrong)
        val textPrimary = Color.White.copy(alpha = OverlayAlpha.textPrimary)
    }

    /**
     * 播放器**专属尺寸**（与 [Sizes] 的通用控件档位分开：这里都是"播放器结构尺寸"）。
     *
     * 通用间距不在这里 —— 那些落 [Spacing]（值相等的直接换 token，不新建体系）。
     */
    object PlayerSizes {
        /** 触控目标下限（M3 硬指标）。 */
        val minTouchTarget = 48.dp
        /** 顶栏 scrim 渐变高度。 */
        val scrimTop = 120.dp
        /** 底栏 scrim 渐变高度。 */
        val scrimBottom = 180.dp
        /** 顶栏最小高度。 */
        val topBarMinHeight = 52.dp
        /** 底栏按钮的**视觉**行高（图标本体的高度档位）。 */
        val bottomRow = 30.dp
        /**
         * 底栏控制行的**布局**高度。
         *
         * 复刻 animeko 后进度条内联进按钮行，行内必须容得下进度条的 48dp 命中区 ——
         * 因此行高按 [minTouchTarget] 而不是 [bottomRow] 给。
         */
        val bottomControlRow = minTouchTarget
        /** 中央大播放键。 */
        val centerButton = 72.dp
        /** 中央大键里的图标。 */
        val centerIcon = 42.dp
        /** 右中锁定按钮。42 → 48：可见填充圆钮**就是**点击面（FilledIconButton 的 clickable
         *  边界 = 容器尺寸），42 不是任何官方档位、也达不到 M3 触控目标，直接归一到 48。 */
        val lockButton = 48.dp
        /** 顶栏/底栏的次级图标（返回、主页…）。20 → **24**：对齐 animeko 顶栏的 24dp。 */
        val iconLarge = 24.dp
        /** 底栏主图标（播放/暂停、下一集）。animeko 用 36dp，取 32 保住信息密度又不显小。 */
        val bottomPrimaryIcon = 32.dp
        /** 底栏次级图标（全屏）。animeko 用 32dp，这里与主图标拉开一档做层级。 */
        val bottomSecondaryIcon = 26.dp
        /** 侧栏面板宽度。 */
        val panelWidth = 156.dp
        /** 进度轨道厚度（视觉）。3 → **5**：animeko 6dp，取 5 既看清又保住"细轨"观感。 */
        val track = 5.dp
        /** 轨道容器高（thumb 光晕的容纳盒）。18 → **22**：对齐 animeko 的 22dp 容器。 */
        val trackBox = 22.dp
        /** 轨道触摸区高（Media3：进度触摸 48dp）。 */
        val trackTouch = 48.dp
        /** thumb 外圈容器。14 → **16**。 */
        val thumbBox = 16.dp
        /** thumb 光晕。15 → **18**。 */
        val thumbGlow = 18.dp
        /** thumb（实心）。9 → **12**：animeko 自绘 12×24，取 12 保住圆形形制。 */
        val thumb = 12.dp
    }

    object Widths {
        /** 表单 / 设置行的内容最大宽度（≈75 字符/行）。 */
        val formMax = 640.dp

        /** 纯阅读正文（简介 / 评论 / 错误说明）的内容最大宽度（≈85 字符/行）。 */
        val readingMax = 720.dp
    }

    object Corners {
        val medium: CornerBasedShape
            @Composable get() = MaterialTheme.shapes.medium

        val large: CornerBasedShape
            @Composable get() = MaterialTheme.shapes.largeIncreased

        /**
         * 胶囊/圆形 —— 走 percent=50 而非写死大数值（999dp/100 等）。
         * M3 `Shapes` 没有暴露 full 档，在 token 层补齐。
         *
         * ⚠️ 这里必须是**具体的 shape 构造**，不能写成 `HanimeDefaults.Corners.pill`
         * 之类的 token 名 —— 那是自引用，会无限递归。
         */
        val pill: CornerBasedShape
            get() = RoundedCornerShape(percent = 50)
    }

    /**
     * 语义性透明度。
     *
     * **只收「有 M3 规格依据」的语义值**，不收装饰性叠加（封面遮罩、渐变、scrim 之类）——
     * 后者的数值是构图的一部分（比如"遮罩要压到刚好能看清白字"），提成 token 反而会诱导
     * 别人去改一个本该按画面调的值。
     *
     * 已知现状：全项目 `alpha = 0.xx` 的硬编码有 20+ 种取值、30 个文件，绝大多数属于上述
     * 装饰性值，暂无收敛计划（审计 P2 备注）。
     */
    object Alpha {
        /** 禁用态内容（M3 规格：onSurface 38%）。 */
        const val disabled = 0.38f
        /** 主题色降级（**非叠层**：如 primary 做次强调时的 alpha）。 */
        const val secondary = 0.82f
    }

    object Colors {
        /**
         * 页面底色 —— 唯一值（`surface`）。M3 里 `background` 已是它的废弃别名，
         * 此前另有 9 处直写 `background`，现统一收敛到此 token。
         */
        val pageSurface: Color
            @Composable get() = MaterialTheme.colorScheme.surface

        /**
         * 卡片填充 —— `surfaceContainerHigh`。原先的 `surfaceBright` 在浅色下与页底
         * 同色（对比度 1.000），卡片靠描边撑存在感；High 档浅/深分离度 1.165/1.234。
         */
        val card: Color
            @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

        val homeVideoCard: Color
            @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow

    }

    val buttonShape: CornerBasedShape
        @Composable get() = MaterialTheme.shapes.extraSmall

    val pressedShape: CornerBasedShape
        @Composable get() = MaterialTheme.shapes.small

    @Composable
    fun shapes() = ButtonDefaults.shapes(
        shape = buttonShape,
        pressedShape = pressedShape,
    )

    @Composable
    fun cardShapes() = ButtonDefaults.shapes(
        shape = Corners.large,
        pressedShape = pressedShape,
    )

    val shapesDefaultAnimationSpec: FiniteAnimationSpec<Float>
        @Composable get() = MaterialTheme.motionScheme.defaultEffectsSpec()
}
