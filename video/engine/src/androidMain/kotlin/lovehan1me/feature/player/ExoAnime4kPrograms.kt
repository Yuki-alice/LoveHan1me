package lovehan1me.feature.player

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import lovehan1me.player.Res
import kotlin.math.roundToInt

// Exo 真 CNN 链的 GL 装配。
//
// 三张 effect 表（与 mpv 侧同一套档位编号，观感对齐）：
// PERFORMANCE = 还原 S（3 卷积 + 1 合并，同尺寸）；
// QUALITY = 还原 M（7 卷积 + 合并）+ 放大 x2 M（合并 + depth-to-space，输出 2x）+ 落地 scaler。
// CNN 权重体取自仓内 MIT `.glsl`，运行时按 `//!DESC` 切分（见 ExoAnime4kPasses）；
// 落地 scaler 用 files/shaders/exo-effects/ 下的现成 GLSL，不在 Kotlin 里嵌大段着色器。
//
// 失败语义：GL 编译/资产缺失一律抛给调用方（引擎的 applyVideoEffects 会吞掉并降级 OFF，
// 绝不崩播放）。不要在这里吞异常——吞了调用方就分不清"没生效"与"生效了"。

internal const val RESTORE_S_ASSET = "Anime4K_Restore_CNN_S.glsl"
internal const val RESTORE_M_ASSET = "Anime4K_Restore_CNN_M.glsl"
internal const val UPSCALE_M_ASSET = "Anime4K_Upscale_CNN_x2_M.glsl"

// 还原档加载结果：convBodies.size() 即卷积 pass 数。
internal data class LoadedRestore(
    val convBodies: List<String>,
    val combineBody: String,
    val featureNames: List<String>,
    val includeOriginal: Boolean,
)

// 放大档加载结果：conv 恒 7 个（见 x2M 的 DESC 表）+ 合并（无原图）+ depth-to-space。
internal data class LoadedUpscale(
    val convBodies: List<String>,
    val combineBody: String,
    val featureNames: List<String>,
    val depthToSpaceBody: String,
)

private val sourceCache = mutableMapOf<String, List<String>>()

private suspend fun passSections(asset: String, expected: Int): List<String> {
    sourceCache[asset]?.let { cached ->
        require(cached.size == expected) { "$asset 段数漂移：期望 $expected，实际 ${cached.size}" }
        return cached
    }
    val source = Res.readBytes("files/shaders/$asset").decodeToString()
    val sections = splitAnime4kPasses(source)
    require(sections.size == expected) {
        "$asset 不是预期的 CNN 结构：期望 $expected 段，实际 ${sections.size}" +
            "（上游文件换版时先核对 //!DESC 表再改调用方）"
    }
    sourceCache[asset] = sections
    return sections
}

internal suspend fun loadRestoreS(): LoadedRestore {
    val sections = passSections(RESTORE_S_ASSET, 4)
    return LoadedRestore(
        convBodies = sections.subList(0, 3),
        combineBody = sections[3],
        featureNames = anime4kCombineFeatures(sections[3]),
        includeOriginal = true,
    )
}

internal suspend fun loadRestoreM(): LoadedRestore {
    val sections = passSections(RESTORE_M_ASSET, 8)
    return LoadedRestore(
        convBodies = sections.subList(0, 7),
        combineBody = sections[7],
        featureNames = anime4kCombineFeatures(sections[7]),
        includeOriginal = true,
    )
}

internal suspend fun loadUpscaleM(): LoadedUpscale {
    val sections = passSections(UPSCALE_M_ASSET, 9)
    return LoadedUpscale(
        convBodies = sections.subList(0, 7),
        combineBody = sections[7],
        featureNames = anime4kCombineFeatures(sections[7]),
        depthToSpaceBody = sections[8],
    )
}

// 还原 effect：PERFORMANCE 传 loadRestoreS()，QUALITY 的还原段传 loadRestoreM()。
internal class Anime4kRestoreEffect(private val loaded: LoadedRestore) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        Anime4kRestoreProgram(
            convBodies = loaded.convBodies,
            combineBody = loaded.combineBody,
            featureNames = loaded.featureNames,
            includeOriginal = loaded.includeOriginal,
            depthToSpaceBody = null,
            upscale = false,
        )
}

// 放大 effect：QUALITY 的第二段（输出 2x，调用方再决定要不要接 scaler 落地）。
internal class Anime4kUpscaleEffect(private val loaded: LoadedUpscale) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        Anime4kRestoreProgram(
            convBodies = loaded.convBodies,
            combineBody = loaded.combineBody,
            featureNames = loaded.featureNames,
            includeOriginal = false,
            depthToSpaceBody = loaded.depthToSpaceBody,
            upscale = true,
        )
}

// 还原/放大共用的多 pass 程序：conv 链 + 合并（+ depth-to-space）。
// upscale=false 时 LoadedUpscale 不会传进来（类型即约束，见 Loaded* 的分工）。
@OptIn(UnstableApi::class)
internal class Anime4kRestoreProgram(
    private val convBodies: List<String>,
    private val combineBody: String,
    private val featureNames: List<String>,
    private val includeOriginal: Boolean,
    private val depthToSpaceBody: String?,
    private val upscale: Boolean,
) : BaseGlShaderProgram(
    /* useHighPrecisionColorComponents = */ true,
    /* texturePoolCapacity = */ 1,
) {
    init {
        require(convBodies.isNotEmpty()) { "空卷积链" }
        require(featureNames.isNotEmpty()) { "合并体没有引用任何特征，资产可能换版" }
        require(!upscale || depthToSpaceBody != null) { "upscale 缺 depth-to-space 体" }
    }

    private val convPrograms: List<GlProgram> = convBodies.mapIndexed { index, body ->
        val input = if (index == 0) "MAIN" else anime4kFeatureName(index - 1)
        newAnime4kProgram(
            anime4kConvPrologue(input) + body + ANIME4K_MAIN_EPILOGUE,
            "conv pass ${index + 1}",
        )
    }
    private val combineProgram: GlProgram = newAnime4kProgram(
        anime4kCombinePrologue(featureNames, includeOriginal) + combineBody + ANIME4K_MAIN_EPILOGUE,
        "feature combine pass",
    )
    private val depthToSpaceProgram: GlProgram? =
        if (upscale) {
            newAnime4kProgram(
                anime4kDepthToSpacePrologue() + (depthToSpaceBody ?: error("unreachable")) +
                    ANIME4K_MAIN_EPILOGUE,
                "depth-to-space pass",
            )
        } else {
            null
        }

    // 特征纹理：全特征合并（M）每 pass 独占一块；单特征（S）两块 ping-pong 足够。
    // upscale 另需一块放合并输出（depth-to-space 的输入）。
    private val featureCount = if (featureNames.size == convBodies.size) convBodies.size else 2
    private val textureCount = featureCount + if (upscale) 1 else 0
    private val intermediateTextures = IntArray(textureCount)
    private val intermediateFramebuffers = IntArray(textureCount)
    private var width = 0
    private var height = 0

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        if (width != inputWidth || height != inputHeight || intermediateTextures[0] == 0) {
            try {
                deleteIntermediateBuffers()
                width = inputWidth
                height = inputHeight
                for (index in intermediateTextures.indices) {
                    intermediateTextures[index] = GlUtil.createTexture(
                        inputWidth,
                        inputHeight,
                        /* useHighPrecisionColorComponents = */ true,
                    )
                    intermediateFramebuffers[index] = GlUtil.createFboForTexture(intermediateTextures[index])
                }
                val texelSize = floatArrayOf(1f / inputWidth, 1f / inputHeight)
                convPrograms.forEach { it.setFloatsUniform("uTexelSize", texelSize) }
                depthToSpaceProgram?.setFloatsUniform(
                    "uInputSize",
                    floatArrayOf(inputWidth.toFloat(), inputHeight.toFloat()),
                )
                depthToSpaceProgram?.setFloatsUniform("uTexelSize", texelSize)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException("Could not configure Anime4K chain", e)
            }
        }
        return if (upscale) Size(inputWidth * 2, inputHeight * 2) else Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val outputFramebuffer = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, outputFramebuffer, 0)
        try {
            convPrograms.forEachIndexed { index, program ->
                val source = if (index == 0) inputTexId else intermediateTextures[textureOf(index - 1)]
                drawSingleInputPass(
                    program,
                    source,
                    intermediateFramebuffers[textureOf(index)],
                    width,
                    height,
                )
            }
            if (upscale) {
                drawCombinePass(inputTexId, intermediateFramebuffers[textureCount - 1])
                val d2s = depthToSpaceProgram ?: error("upscale 缺 depth-to-space 程序")
                GlUtil.focusFramebufferUsingCurrentContext(outputFramebuffer[0], width * 2, height * 2)
                d2s.use()
                d2s.setSamplerTexIdUniform(
                    "uFeatureSampler",
                    intermediateTextures[textureCount - 1],
                    /* texUnitIndex = */ 0,
                )
                d2s.setSamplerTexIdUniform("uOriginalSampler", inputTexId, /* texUnitIndex = */ 1)
                d2s.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first = */ 0, /* count = */ 4)
                GlUtil.checkGlError()
            } else {
                drawCombinePass(inputTexId, outputFramebuffer[0])
            }
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    // pass i 的输出纹理：全特征模式独占，否则两块 ping-pong。
    private fun textureOf(passIndex: Int): Int =
        if (featureNames.size == convBodies.size) passIndex else passIndex % 2

    private fun drawCombinePass(inputTexId: Int, outputFramebuffer: Int) {
        GlUtil.focusFramebufferUsingCurrentContext(outputFramebuffer, width, height)
        combineProgram.use()
        if (featureNames.size == convBodies.size) {
            featureNames.forEachIndexed { index, _ ->
                combineProgram.setSamplerTexIdUniform(
                    "uFeature$index",
                    intermediateTextures[index],
                    index,
                )
            }
        } else {
            // 单特征（S）：只读最后一块 ping-pong 输出。
            combineProgram.setSamplerTexIdUniform(
                "uFeature0",
                intermediateTextures[(convBodies.size - 1) % 2],
                0,
            )
        }
        if (includeOriginal) {
            combineProgram.setSamplerTexIdUniform(
                "uOriginalSampler",
                inputTexId,
                featureNames.size,
            )
        }
        combineProgram.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first = */ 0, /* count = */ 4)
        GlUtil.checkGlError()
    }

    override fun release() {
        try {
            deleteIntermediateBuffers()
            convPrograms.forEach(GlProgram::delete)
            combineProgram.delete()
            depthToSpaceProgram?.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException("Could not release Anime4K chain", e)
        }
        super.release()
    }

    private fun deleteIntermediateBuffers() {
        for (index in intermediateTextures.indices) {
            if (intermediateFramebuffers[index] != 0) {
                GlUtil.deleteFbo(intermediateFramebuffers[index])
                intermediateFramebuffers[index] = 0
            }
            if (intermediateTextures[index] != 0) {
                GlUtil.deleteTexture(intermediateTextures[index])
                intermediateTextures[index] = 0
            }
        }
    }
}

// 落地 scaler：单趟径向 EWA 近似的 ewa_lanczossharp（素材见 files/shaders/exo-effects/）。
// 观感对标桌面 mpv 的 presentation 链 —— sigmoid 放大 + 0.7 抗振铃，
// 但不在移动端多分配一张全尺寸中间纹理。片源与视口尺寸都已知时才挂（见 effectsFor）。
//
// 着色器源码由挂起上下文读好后传进来：资产读取是 IO，而 GlProgram 在 GL 线程构造，
// 在那条线程上读 APK 会把出帧卡住。
internal class LanczosSharpScalerEffect(
    private val vertexSource: String,
    private val fragmentSource: String,
    private val viewportWidth: Int,
    private val viewportHeight: Int,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        LanczosSharpScalerProgram(vertexSource, fragmentSource, viewportWidth, viewportHeight)
}

@OptIn(UnstableApi::class)
private class LanczosSharpScalerProgram(
    vertexSource: String,
    fragmentSource: String,
    private val viewportWidth: Int,
    private val viewportHeight: Int,
) : BaseGlShaderProgram(
    /* useHighPrecisionColorComponents = */ true,
    /* texturePoolCapacity = */ 1,
) {
    private val program: GlProgram = try {
        GlProgram(vertexSource, fragmentSource).also {
            it.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
            )
        }
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException("Could not compile desktop-style scaler", e)
    }

    private var inputWidth = 0
    private var inputHeight = 0

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        this.inputWidth = inputWidth
        this.inputHeight = inputHeight
        val scale = minOf(
            viewportWidth.toDouble() / inputWidth,
            viewportHeight.toDouble() / inputHeight,
        )
        return Size(
            (inputWidth * scale).roundToInt().coerceAtLeast(1),
            (inputHeight * scale).roundToInt().coerceAtLeast(1),
        )
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex = */ 0)
            program.setFloatsUniform(
                "uInputSize",
                floatArrayOf(inputWidth.toFloat(), inputHeight.toFloat()),
            )
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first = */ 0, /* count = */ 4)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        try {
            program.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException("Could not release desktop-style scaler", e)
        }
        super.release()
    }
}

private fun drawSingleInputPass(
    program: GlProgram,
    inputTexId: Int,
    outputFramebuffer: Int,
    width: Int,
    height: Int,
) {
    GlUtil.focusFramebufferUsingCurrentContext(outputFramebuffer, width, height)
    program.use()
    program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex = */ 0)
    program.bindAttributesAndUniforms()
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first = */ 0, /* count = */ 4)
    GlUtil.checkGlError()
}

@Throws(GlUtil.GlException::class)
private fun newAnime4kProgram(fragmentShader: String, passName: String): GlProgram = try {
    GlProgram(VERTEX_SHADER, fragmentShader).also { program ->
        program.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
        )
        val identity = GlUtil.create4x4IdentityMatrix()
        program.setFloatsUniform("uTransformationMatrix", identity)
        program.setFloatsUniform("uTexTransformationMatrix", identity)
    }
} catch (e: GlUtil.GlException) {
    throw VideoFrameProcessingException("Could not compile $passName", e)
}

// 顶点着色器：与 media3-effect 自带 vertex_shader_transformation_es2 同接口
// （属性/uniform/varying 同名），逻辑为直通全屏 quad。
private const val VERTEX_SHADER = """
#version 100
attribute vec4 aFramePosition;
uniform mat4 uTransformationMatrix;
uniform mat4 uTexTransformationMatrix;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = uTransformationMatrix * aFramePosition;
  vec4 texturePosition = vec4(aFramePosition.x * 0.5 + 0.5,
                              aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);
  vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
}
"""

// scaler 的两段素材：径向 EWA 近似版 ewa_lanczossharp（顶点无矩阵 uniform，
// 与 CNN 链用的 [VERTEX_SHADER] 不是一张表，故不共用）。
private const val LANZOS_VERTEX_ASSET = "files/shaders/exo-effects/ewa_lanczossharp.vert"
private const val LANZOS_FRAGMENT_ASSET = "files/shaders/exo-effects/ewa_lanczossharp.frag"

private var scalerSources: Pair<String, String>? = null

/** 读 scaler 的两段着色器（进程内缓存一次；切档不该反复解 APK）。 */
internal suspend fun loadScalerSources(): Pair<String, String> {
    scalerSources?.let { return it }
    val sources = try {
        Res.readBytes(LANZOS_VERTEX_ASSET).decodeToString() to
            Res.readBytes(LANZOS_FRAGMENT_ASSET).decodeToString()
    } catch (e: Exception) {
        throw VideoFrameProcessingException("缺少 scaler 素材 $LANZOS_FRAGMENT_ASSET", e)
    }
    scalerSources = sources
    return sources
}

