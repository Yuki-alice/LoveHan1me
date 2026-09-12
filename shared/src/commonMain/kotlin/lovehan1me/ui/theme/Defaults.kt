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
    }

    object Colors {
        val pageSurface: Color
            @Composable get() = MaterialTheme.colorScheme.surfaceContainer

        val card: Color
            @Composable get() = MaterialTheme.colorScheme.surfaceBright

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
