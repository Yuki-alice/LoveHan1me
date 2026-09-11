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

/**
 * 阶段一②：ExoPlayer 内核的视频超分（Anime4K **近似**，单 pass）。
 *
 * 背景：
 * - mpv 系引擎（[MpvPlaybackEngine] / Desktop）直接挂 mpv 多 pass shader；
 * - `composeResources/files/shaders/` 下的 9 个 `.glsl` 全是 mpv 专用格式
 *  （`//!HOOK` / `//!BIND` / `//!SAVE` + `hook()` + `MAIN_texOff` 等隐式 uniform），
 *   **不能**直接喂给 [GlProgram]，忠实移植 = 逐 pass 改写 + 链式串联，留待后续迭代；
 * - 本文件先调通链路（`setVideoEffects` + 切档重建续播 + 失败降级 OFF），
 *   三档用轻量单 pass 近似 shader：luma 域 unsharp-mask 提细节 +
 *   overshoot 限幅（防 ringing，取 Anime4K `Clamp_Highlights` 的精神）+
 *   高光软钳制。效果弱于 mpv 真 Anime4K，但零崩溃、可热切换验证。
 *
 * 档位编号与 [lovehan1me.core.util.MpvShaders] 对齐（UI 传 0/1/2，两引擎语义一致）：
 * - [OFF]：不挂 effect（空实现，不创建 GL 程序）；
 * - [PERFORMANCE]：5-tap 锐化，约 5 次纹理采样，低端机也吃得消；
 * - [QUALITY]：13-tap（8 邻域 + 宽轴 4 采样）+ 噪声门限，细节增益更强。
 *
 * 实现模板取自 media3-effect 1.10.1 自带的 `HslShaderProgram`
 * （`GlEffect` + [BaseGlShaderProgram] + [GlProgram]，顶点着色器接口保持兼容）；
 * 1.10.1 已无 `SingleFrameGlShaderProgram`，不要按旧文档找它。
 */
object ExoSuperResolution {
    const val OFF = 0
    const val PERFORMANCE = 1
    const val QUALITY = 2

    /** UI 传进来的 index 是否为有效档位。 */
    fun isValid(level: Int): Boolean = level == OFF || level == PERFORMANCE || level == QUALITY

    /** 该档位需要挂的 effects；OFF 返回空表（= 不动原管线）。 */
    fun effectsFor(level: Int): List<GlEffect> =
        if (level == OFF) emptyList() else listOf(Anime4KApproxEffect(level))
}

/**
 * 单 pass 近似超分 effect。segment：把 [level] 带进 shader program，
 * 档位参数（强度）烘焙进 fragment shader 字符串（切换档位 = 换 program = 重建管线）。
 */
@OptIn(UnstableApi::class)
class Anime4KApproxEffect(val level: Int) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
        Anime4KApproxShaderProgram(context, level, useHdr)
}

/**
 * 单输入单输出的卷积式 shader program。
 *
 * 约定（与 media3-effect 自带 shader 互通）：
 * - 输入是采样器 2D 纹理（`uTexSampler`），顶点接口与
 *   `vertex_shader_transformation_es2` 一致（`aFramePosition` /
 *   `uTransformationMatrix` / `uTexTransformationMatrix` / `vTexSamplingCoord`）；
 * - 输出与输入同尺寸（纯画质增强，不做缩放）；
 * - 卷积所需 texel 步长由 [configure] 按真实输入尺寸换算后经 `uTexelSize` 传入。
 */
@OptIn(UnstableApi::class)
class Anime4KApproxShaderProgram(
    context: Context,
    level: Int,
    useHdr: Boolean,
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {
    private val glProgram: GlProgram
    private var texelWidth = 0f
    private var texelHeight = 0f

    init {
        // HslShaderProgram 在 HDR 直接抛；卷积是线性数学，对输入色彩空间不敏感，
        // HDR 下照跑（效果未经验证，真机走查时重点看一集 HDR 片源）。
        try {
            glProgram = GlProgram(VERTEX_SHADER, fragmentShaderFor(level))
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }

        // 全屏 quad（NDC -1..1），与 HslShaderProgram 同写法。
        glProgram.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
        )
        val identity = GlUtil.create4x4IdentityMatrix()
        glProgram.setFloatsUniform("uTransformationMatrix", identity)
        glProgram.setFloatsUniform("uTexTransformationMatrix", identity)
        glProgram.setFloatUniform("uStrength", strengthFor(level))
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        texelWidth = 1f / inputWidth.coerceAtLeast(1)
        texelHeight = 1f / inputHeight.coerceAtLeast(1)
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            glProgram.setFloatsUniform("uTexelSize", floatArrayOf(texelWidth, texelHeight))
            glProgram.bindAttributesAndUniforms()
            // 四顶点 triangle strip 拼成 quad，与 HslShaderProgram 同写法。
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private companion object {
        // 顶点着色器：接口与 media3-effect 的 vertex_shader_transformation_es2 对齐
        // （属性/uniform/varying 同名），逻辑为直通全屏 quad。
        const val VERTEX_SHADER = """
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

        // PERFORMANCE：5-tap（中心 + 上下左右）luma unsharp mask。
        // overshoot 限幅 ±0.08（防 ringing），再做高光软钳制。
        const val FRAGMENT_PERFORMANCE = """
#version 100
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uTexelSize;
uniform float uStrength;
varying vec2 vTexSamplingCoord;
float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
void main() {
  vec2 t = vTexSamplingCoord;
  vec4 center = texture2D(uTexSampler, t);
  vec3 c = center.rgb;
  vec3 n = texture2D(uTexSampler, t + vec2(0.0, uTexelSize.y)).rgb;
  vec3 s = texture2D(uTexSampler, t - vec2(0.0, uTexelSize.y)).rgb;
  vec3 e = texture2D(uTexSampler, t + vec2(uTexelSize.x, 0.0)).rgb;
  vec3 w = texture2D(uTexSampler, t - vec2(uTexelSize.x, 0.0)).rgb;
  vec3 blur = (n + s + e + w) * 0.25;
  float detail = clamp(luma(c - blur), -0.08, 0.08);
  vec3 outc = c + detail * uStrength;
  float l = luma(outc);
  float k = smoothstep(0.85, 1.0, l);
  outc = mix(outc, clamp(outc, 0.0, 1.0), k);
  gl_FragColor = vec4(outc, center.a);
}
"""

        // QUALITY：13-tap（8 邻域 + 宽轴 ±1.5 共 4 采样）+ 噪声门限。
        // 微小细节（|detail| < 0.015）视为噪声直接压掉，避免放大小噪点；
        // 增益更强，GPU 开销约为 PERFORMANCE 的 2~3 倍。
        const val FRAGMENT_QUALITY = """
#version 100
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uTexelSize;
uniform float uStrength;
varying vec2 vTexSamplingCoord;
float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
void main() {
  vec2 t = vTexSamplingCoord;
  vec2 px = vec2(uTexelSize.x, 0.0);
  vec2 py = vec2(0.0, uTexelSize.y);
  vec4 center = texture2D(uTexSampler, t);
  vec3 c = center.rgb;
  vec3 n = texture2D(uTexSampler, t + py).rgb;
  vec3 s = texture2D(uTexSampler, t - py).rgb;
  vec3 e = texture2D(uTexSampler, t + px).rgb;
  vec3 w = texture2D(uTexSampler, t - px).rgb;
  vec3 ne = texture2D(uTexSampler, t + px + py).rgb;
  vec3 nw = texture2D(uTexSampler, t - px + py).rgb;
  vec3 se = texture2D(uTexSampler, t + px - py).rgb;
  vec3 sw = texture2D(uTexSampler, t - px - py).rgb;
  vec3 blur8 = (n + s + e + w + ne + nw + se + sw) * 0.125;
  vec3 wide = (texture2D(uTexSampler, t + 1.5 * px).rgb
      + texture2D(uTexSampler, t - 1.5 * px).rgb
      + texture2D(uTexSampler, t + 1.5 * py).rgb
      + texture2D(uTexSampler, t - 1.5 * py).rgb) * 0.25;
  vec3 blur = blur8 * 0.7 + wide * 0.3;
  float detail = luma(c - blur);
  float gated = detail * smoothstep(0.0, 0.015, abs(detail));
  gated = clamp(gated, -0.10, 0.10);
  vec3 outc = c + gated * uStrength;
  float l = luma(outc);
  float k = smoothstep(0.85, 1.0, l);
  outc = mix(outc, clamp(outc, 0.0, 1.0), k);
  gl_FragColor = vec4(outc, center.a);
}
"""

        fun fragmentShaderFor(level: Int): String =
            if (level == ExoSuperResolution.QUALITY) FRAGMENT_QUALITY else FRAGMENT_PERFORMANCE

        // 强度经真机走查再调；初值按“肉眼可见、不过度锐化”取。
        fun strengthFor(level: Int): Float =
            if (level == ExoSuperResolution.QUALITY) 4.5f else 3.0f
    }
}
