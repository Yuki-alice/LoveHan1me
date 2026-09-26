package lovehan1me.feature.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Exo CNN 模板构造的纯逻辑测试（Gate3-P4）。
// 注意：这里只用**自造**的合成素材测切分与替换形状，
// 不内嵌 MIT 权重文本（权重由仓内 `.glsl` 在运行时提供，见 MpvShadersDesktopTest 那类落盘测试）。
class ExoAnime4kPassesTest {

    // 两个 DESC 段的合成素材：第一段带 HOOK/BIND/SAVE 指令行，第二段只有 DESC。
    private val synthetic = """
        //!DESC First-Conv-4x3x3x3
        //!HOOK MAIN
        //!BIND MAIN
        //!SAVE conv2d_tf
        #define go(x_off, y_off) (MAIN_texOff(vec2(x_off, y_off)))
        vec4 hook() { return go(vec2(0.0)); }
        //!DESC Second-Conv-3x1x1x8
        #define g_0 (max((conv2d_tf_tex(conv2d_tf_pos)), 0.0))
        vec4 hook() { return g_0 + MAIN_tex(MAIN_pos); }
    """.trimIndent()

    @Test
    fun `按DESC切分并去掉指令行`() {
        val passes = splitAnime4kPasses(synthetic)
        assertEquals(2, passes.size)
        // DESC 描述行与 //! 指令行都不该进体
        assertFalse(passes[0].contains("DESC"))
        assertFalse(passes[0].contains("//!"))
        assertFalse(passes[1].contains("//!"))
        // 体函数与权重宏保留
        assertTrue(passes[0].contains("vec4 hook()"))
        assertTrue(passes[0].contains("MAIN_texOff"))
        assertTrue(passes[1].contains("conv2d_tf_tex"))
    }

    @Test
    fun `特征名规律与各glsl的SAVE行一致`() {
        assertEquals("conv2d_tf", anime4kFeatureName(0))
        assertEquals("conv2d_1_tf", anime4kFeatureName(1))
        assertEquals("conv2d_6_tf", anime4kFeatureName(6))
        assertEquals(
            listOf("conv2d_tf", "conv2d_1_tf", "conv2d_2_tf"),
            anime4kRestoreFeatureNames(3),
        )
    }

    @Test
    fun `卷积前导按输入名生成采样定义`() {
        val prologue = anime4kConvPrologue("conv2d_1_tf")
        assertTrue(prologue.contains("conv2d_1_tf_texOff(off)"))
        assertTrue(prologue.contains("uTexelSize"))
        assertTrue(prologue.contains("varying vec2 vTexSamplingCoord"))
        assertFalse(prologue.contains("MAIN_texOff"))
    }

    @Test
    fun `合并前导按特征数绑定采样器_原图按开关出`() {
        val withOriginal = anime4kCombinePrologue(listOf("conv2d_tf", "conv2d_1_tf"), true)
        assertTrue(withOriginal.contains("uniform sampler2D uFeature0;"))
        assertTrue(withOriginal.contains("uniform sampler2D uFeature1;"))
        assertTrue(withOriginal.contains("uniform sampler2D uOriginalSampler;"))
        assertTrue(withOriginal.contains("conv2d_1_tf_tex(pt)"))
        assertTrue(withOriginal.contains("MAIN_tex(pt)"))

        // upscale 的合并没有原图（见 x2M 的 BIND 表）：不应出现原图采样器
        val noOriginal = anime4kCombinePrologue(listOf("conv2d_tf"), false)
        assertTrue(noOriginal.contains("uFeature0"))
        assertFalse(noOriginal.contains("uOriginalSampler"))
        assertFalse(noOriginal.contains("MAIN_tex"))
    }

    @Test
    fun `depthToSpace前导给出尺寸与步长`() {
        val prologue = anime4kDepthToSpacePrologue()
        assertTrue(prologue.contains("conv2d_last_tf_size"))
        assertTrue(prologue.contains("conv2d_last_tf_pt"))
        assertTrue(prologue.contains("uInputSize"))
        assertTrue(prologue.contains("MAIN_pos"))
    }

    @Test
    fun `拼接后的源码每行指令独立成行`() {
        // 真机教训（Gate4-2）：前导缺尾换行会把体的首行 #define 粘到上一行尾，
        // 预处理器认不出，Mali 报 "No matching function for call to 'go_0'"。
        val body = "#define go(x) (MAIN_texOff(x))\nvec4 hook() { return go(vec2(0.0)); }"
        val src = anime4kConvPrologue("MAIN") + body + ANIME4K_MAIN_EPILOGUE
        for (line in src.lineSequence()) {
            if ("#define" in line) {
                assertTrue(
                    line.trimStart().startsWith("#define"),
                    "指令被粘住：$line",
                )
            }
        }
        assertTrue(
            src.lineSequence().any { it.trimStart().startsWith("vec4 hook()") },
            "体函数必须独立成行",
        )
    }

    @Test
    fun `合并体按实际引用扫出特征_单特征与全特征统一`() {
        // 还原 S 的合并只采样最后一个特征 + 原图（点采样 _tex/_pos，非卷积的 _texOff）
        val single = anime4kCombineFeatures(
            "#define go(x) (conv2d_2_tf_tex(conv2d_2_tf_pos))\n" +
                "vec4 hook() { return go(vec2(0.0)) + MAIN_tex(MAIN_pos); }",
        )
        assertEquals(listOf("conv2d_2_tf"), single)

        // M 的合并采样全部 7 个（出现顺序打乱也按序号排）
        val full = anime4kCombineFeatures(
            "#define g_1 (conv2d_1_tf_tex(conv2d_1_tf_pos))\n" +
                "#define g_0 (conv2d_tf_tex(conv2d_tf_pos))\n" +
                "#define g_4 (conv2d_3_tf_tex(conv2d_3_tf_pos))\n" +
                "vec4 hook() { return g_0 + g_1 + g_4; }",
        )
        assertEquals(listOf("conv2d_tf", "conv2d_1_tf", "conv2d_3_tf"), full)
    }
}
