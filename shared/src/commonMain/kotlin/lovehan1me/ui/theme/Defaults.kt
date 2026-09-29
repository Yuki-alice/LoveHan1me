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
     * M3 官方**控件尺寸档**（icon button / 同类方形控件的**容器视觉尺寸**）：M 档 56dp。
     *
     * 与 [Spacing] 是两回事：Spacing 是留白阶梯，这里是控件自身的档位。
     * 也**不等于触控目标** —— 触控目标恒 ≥48dp（M3 硬指标），由命中区放大处理。
     *
     * 替换原则与 [Spacing] 相同：**值一一对应**，换 token 不改像素。
     */
    object Sizes {
        /** M：56dp —— 需要强调的主控按钮（如浮动操作键）。 */
        val controlM = 56.dp
    }

    /**
     * 叠层基色 —— 只剩纯白基色一档。
     *
     * 播放器那套叠层色表（scrim 渐变 / 轨道 / 玻璃底 / 叠层文字档）已随播放器控件
     * 整体迁去 `:video:ui`，本对象不再承载它们。
     */
    object Overlay {
        /** 叠层上的图标/文字基色（纯白）。 */
        val onScrim = Color.White
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
         * 内容 sheet 的形状：**起始侧上下两角 28dp**（值等于 `shapes.extraLarge`），
         * 末端两角为直角。
         *
         * 宽屏下「左侧 chrome / 右侧内容」的分界**只靠这个圆角 + 底色差**，
         * 不再画细分隔线 —— 同系底色下 1px 细线约等于没有（见 `MainScaffold`
         * 宽屏分支的注释）。首页宽屏与设置双栏共用本 token，避免两处
         * 各写一个 28dp 各自漂移。
         *
         * 只圆**起始侧**：sheet 贴窗口右半边，末端两角落在屏幕边缘，圆角无意义；
         * 起始侧两角才是贴着 chrome 的可见边缘，必须都圆 —— 只圆左上会让 sheet
         * 的左下与 chrome 之间留出一个直角缺口（同一块 sheet 一半圆一半方）。
         */
        val contentSheet: CornerBasedShape
            get() = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)

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
         * 页面 / 内容区底色 —— `surfaceContainerLowest`（浅色 T100 即纯白，深色 T4）。
         *
         * 与导航容器（[Bars.navigationContainerColor]，`surfaceContainer`，T94）相差 6 个 tone，
         * chrome 与内容的分界靠这个差值成立。此前取 `surface`（T98）只差 4 个 tone，
         * 分界几乎看不见，chrome 与内容糊在一起。
         *
         * M3 里 `background` 是 `surface` 的废弃别名；本 token 仍是全项目页面底色的唯一出口。
         */
        val pageSurface: Color
            @Composable get() = MaterialTheme.colorScheme.surfaceContainerLowest

        /**
         * 卡片填充 —— `surfaceContainerHigh`（浅色 T92）。原先的 `surfaceBright` 在浅色下
         * 与页底同色（对比度 1.000），卡片靠描边撑存在感；High 档浅/深分离度 1.165/1.234。
         */
        val card: Color
            @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

        /** 首页视频卡填充 —— `surfaceContainerLow`（比 [card] 浅一档，密集列表里更透气）。 */
        val homeVideoCard: Color
            @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow
    }

    /**
     * 导航 chrome 的语义色。
     *
     * **只保留真正有调用方的角色。** 上一版这里另有 `pageContentBackgroundColor`（与
     * [Colors.pageSurface] 取值重复）和 `topAppBarColors()`（与 `HanimeTopAppBar` 自带的
     * 实现冲突：前者 `scrolledContainerColor` 取 `surfaceContainer`、后者取
     * `surfaceContainerHigh`），两者都零调用 —— 留着会让后来人以为"改这里能生效"，
     * 实际改的是死值。
     */
    object Bars {
        /**
         * 导航容器色：宽屏 Rail 的底、设置双栏左栏的底。
         *
         * `surfaceContainer`（浅色 T94）。移动端底栏不显式传色，走 M3 `NavigationBar`
         * 默认值，恰好也是 `surfaceContainer` —— **同一个导航角色在两种窗口宽度下必须是
         * 同一个色**。此前宽屏分支硬编码 `surfaceContainerLow`（T96），比紧凑底栏浅一档，
         * 同一角色随窗口宽度变色。
         */
        val navigationContainerColor: Color
            @Composable get() = MaterialTheme.colorScheme.surfaceContainer
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
