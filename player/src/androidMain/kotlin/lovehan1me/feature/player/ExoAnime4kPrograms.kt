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

// Exo 真 CNN 链的 GL 装配（Gate3-P4）。
//
// 形态照 animeko 的三 effect 设计（只搬设计，模板与胶水全自写，AGPL 代码零复制）：
// PERFORMANCE = 还原 S（N 卷积 + 1 合并，同尺寸）；
// QUALITY = 还原 M + 放大 x2 M（7 卷积 + 合并 + depth-to-space，输出 2x）[+ 落地 scaler]。
// CNN 权重体全部取自仓内 MIT `.glsl`，运行时按 `//!DESC` 切分（见 ExoAnime4kPasses）。
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

// 落地 scaler：把上游输出 fit 到视口（自研 lanczos-2 近似 + 钳制抗振铃）。
// 只在"真需要放大且视口已知"时挂（见 effectsFor 的门控），缩小路径不走这里。
internal class FitLanczosScalerEffect(
    private val viewportWidth: Int,
    private val viewportHeight: Int,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        FitLanczosScalerProgram(viewportWidth, viewportHeight)
}

@OptIn(UnstableApi::class)
private class FitLanczosScalerProgram(
    private val viewportWidth: Int,
    private val viewportHeight: Int,
) : BaseGlShaderProgram(
    /* useHighPrecisionColorComponents = */ true,
    /* texturePoolCapacity = */ 1,
) {
    private val program: GlProgram = newAnime4kProgram(SCALER_FRAGMENT, "fit scaler")
    private var inputWidth = 0
    private var inputHeight = 0

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        this.inputWidth = inputWidth
        this.inputHeight = inputHeight
        val scale = minOf(
            viewportWidth.toDouble() / inputWidth,
            viewportHeight.toDouble() / inputHeight,
        )
        program.setFloatsUniform(
            "uTexelSize",
            floatArrayOf(1f / inputWidth, 1f / inputHeight),
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
            throw VideoFrameProcessingException(e)
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

// 落地 scaler 片元：lanczos-2（4x4）+ 十字邻域钳制抗振铃（自研近似，
// 对标 mpv ewa_lanczossharp presentation 链的观感，单 pass 省移动端显存）。
private const val SCALER_FRAGMENT = """
#version 100
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uTexelSize;
varying vec2 vTexSamplingCoord;
float lanczosWeight(float x) {
  x = abs(x);
  if (x < 0.0001) return 1.0;
  if (x >= 2.0) return 0.0;
  float pix = 3.14159265 * x;
  return 2.0 * sin(pix) * sin(pix * 0.5) / (pix * pix);
}
void main() {
  vec2 src = vTexSamplingCoord / uTexelSize;
  vec2 base = floor(src - 1.5);
  vec3 sum = vec3(0.0);
  float wsum = 0.0;
  vec3 mn = vec3(1.0e9);
  vec3 mx = vec3(-1.0e9);
  for (int j = 0; j < 4; j++) {
    for (int i = 0; i < 4; i++) {
      vec2 tap = base + vec2(float(i), float(j)) + 0.5;
      vec2 d = tap - src;
      float w = lanczosWeight(d.x) * lanczosWeight(d.y);
      vec3 c = texture2D(uTexSampler, tap * uTexelSize).rgb;
      sum += c * w;
      wsum += w;
      if (abs(d.x) <= 1.0 && abs(d.y) <= 1.0) { mn = min(mn, c); mx = max(mx, c); }
    }
  }
  vec3 outc = sum / max(wsum, 0.000001);
  gl_FragColor = vec4(clamp(outc, mn, mx), 1.0);
}
"""
