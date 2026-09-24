package lovehan1me.feature.player

// Exo 真 CNN 链的纯构造部分（Gate3-P4，无 Android 依赖，可进 commonTest）。
//
// 背景：`composeResources/files/shaders/` 下的 Anime4K `.glsl` 是 mpv 专用格式
// （`//!DESC/HOOK/BIND/SAVE` + `hook()` + `MAIN_texOff` 等隐式 uniforme），
// 不能直接喂给 Media3 的 GlProgram。做法是按 `//!DESC` 切分出各 pass 的函数体，
// 再为每个 pass 生成一段"前导定义"（把 mpv 隐式 uniform 翻译成我们的 sampler），
// 最后拼 `void main() { gl_FragColor = hook(); }`。
// 体函数（含全部 CNN 权重）原样取自 MIT 上游文件，本文件只生成翻译层。

// 按 `//!DESC` 切分：去掉 DESC 描述行与全部 `//!` 指令行，剩下的就是 pass 体。
// （体内的 `#define go_*/g_*` 与 `vec4 hook()` 原样保留。）
fun splitAnime4kPasses(source: String): List<String> =
    source.split(Regex("(?m)^//!DESC "))
        .drop(1)
        .map { section ->
            section.lineSequence()
                .drop(1)
                .filterNot { it.startsWith("//!") }
                .joinToString("\n")
        }

// 第 i 个卷积 pass 的输入纹理名：第 0 个吃 MAIN，其余吃上一个 pass 的 SAVE 名。
// SAVE 名规律（见各 `.glsl` 的 `//!SAVE` 行）：首个叫 conv2d_tf，之后叫 conv2d_N_tf。
fun anime4kFeatureName(index: Int): String =
    if (index == 0) "conv2d_tf" else "conv2d_${index}_tf"

// 卷积 pass 的前导：把 mpv 隐式 uniform 翻译成我们的 uTexSampler + uTexelSize。
// 体内只用到 `<input>_texOff`（3x3 卷积采样），`_tex/_pos` 一并给出以备它用。
fun anime4kConvPrologue(inputName: String): String = """#version 100
    precision highp float;
    uniform sampler2D uTexSampler;
    uniform vec2 uTexelSize;
    varying vec2 vTexSamplingCoord;
    #define ${inputName}_texOff(off) texture2D(uTexSampler, vTexSamplingCoord + (off) * uTexelSize)
    #define ${inputName}_tex(pt) texture2D(uTexSampler, (pt))
    #define ${inputName}_pos vTexSamplingCoord
""".trimIndent()

// 合并 pass 的前导：N 个特征各占一个 sampler（uFeatureK），
// 还原残差用的原图占 uOriginalSampler（upscale 的合并没有原图，见 x2M 的 BIND 表）。
fun anime4kCombinePrologue(featureNames: List<String>, includeOriginal: Boolean): String =
    buildString {
        appendLine("#version 100")
        appendLine("precision highp float;")
        featureNames.forEachIndexed { index, _ ->
            appendLine("uniform sampler2D uFeature$index;")
        }
        if (includeOriginal) appendLine("uniform sampler2D uOriginalSampler;")
        appendLine("varying vec2 vTexSamplingCoord;")
        featureNames.forEachIndexed { index, name ->
            appendLine("#define ${name}_tex(pt) texture2D(uFeature$index, (pt))")
            appendLine("#define ${name}_pos vTexSamplingCoord")
        }
        if (includeOriginal) {
            appendLine("#define MAIN_tex(pt) texture2D(uOriginalSampler, (pt))")
            appendLine("#define MAIN_pos vTexSamplingCoord")
        }
    }

// depth-to-space（x2 上采样收尾）的前导：特征 + 原图双 sampler，
// 另需输入尺寸（算子像素位置）与 texel（采样点回中）。
fun anime4kDepthToSpacePrologue(): String = """#version 100
    precision highp float;
    uniform sampler2D uFeatureSampler;
    uniform sampler2D uOriginalSampler;
    uniform vec2 uTexelSize;
    uniform vec2 uInputSize;
    varying vec2 vTexSamplingCoord;
    #define conv2d_last_tf_tex(pt) texture2D(uFeatureSampler, (pt))
    #define conv2d_last_tf_pos vTexSamplingCoord
    #define conv2d_last_tf_size uInputSize
    #define conv2d_last_tf_pt uTexelSize
    #define MAIN_tex(pt) texture2D(uOriginalSampler, (pt))
    #define MAIN_pos vTexSamplingCoord
""".trimIndent()

// 每个 pass 体的收尾：mpv 的 hook() 即主函数体。
const val ANIME4K_MAIN_EPILOGUE = "\nvoid main() { gl_FragColor = hook(); }\n"

// 还原档的合并需要哪些特征：conv 体数量即特征数（SAVE 名按 anime4kFeatureName 规律）。
fun anime4kRestoreFeatureNames(convCount: Int): List<String> =
    List(convCount) { anime4kFeatureName(it) }

// 从合并体中扫出它实际采样的特征名（`X_tex(` / `X_pos` 引用），按特征序号排序。
// 还原 S 只用最后一个特征（见其合并体的 go_ 定义），M 用全部 7 个；
// 用"实际引用"而非"体数量"判定，两种形状统一处理。
fun anime4kCombineFeatures(combineBody: String): List<String> {
    val refs = Regex("""(conv2d(?:_\d+)?_tf)_(?:tex|pos)\b""")
        .findAll(combineBody)
        .map { it.groupValues[1] }
        .toSet()
    return refs.sortedBy { name ->
        if (name == "conv2d_tf") 0
        else name.removePrefix("conv2d_").removeSuffix("_tf").toIntOrNull()?.plus(1) ?: Int.MAX_VALUE
    }
}
