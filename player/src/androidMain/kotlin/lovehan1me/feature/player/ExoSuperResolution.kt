package lovehan1me.feature.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect

// ExoPlayer 内核的视频超分（Gate3-P4：真 Anime4K CNN 链，替换此前的单 pass 锐化近似）。
//
// 档位（与 mpv 侧编号一致，UI 传 0/1/2）：
// - OFF：不挂 effect（空实现，不创建 GL 程序）；
// - PERFORMANCE：官方 Anime4K Restore CNN S（4 pass + 2 FP16 ping-pong，同尺寸）；
// - QUALITY：官方 Restore CNN M + Upscale CNN x2 M（输出 2x），真需要放大时再接
//   落地 scaler fit 到视口；不需要放大时只做还原（省掉"放大再缩回"，见分辨率门控）。
//
// 素材：`composeResources/files/shaders/` 下的 MIT `.glsl`（mpv 格式），
// 运行时切分 + 模板注入（见 ExoAnime4kPasses / ExoAnime4kPrograms）。
// 失败语义不变：GL 编译/资产异常一律抛给引擎，由它降级 OFF（绝不崩播放）。
object ExoSuperResolution {
    const val OFF = 0
    const val PERFORMANCE = 1
    const val QUALITY = 2

    // UI 传进来的 index 是否为有效档位。
    fun isValid(level: Int): Boolean = level == OFF || level == PERFORMANCE || level == QUALITY

    // 该档位需要挂的 effects；OFF 返回空表（= 不动原管线）。
    // 挂载前读一次资产（内存缓存，见 sourceCache），调用方在协程内调（引擎的 applyVideoEffects）。
    suspend fun effectsFor(
        level: Int,
        videoWidth: Int,
        videoHeight: Int,
        surfaceWidth: Int,
        surfaceHeight: Int,
    ): List<GlEffect> = when (level.coerceIn(OFF, QUALITY)) {
        PERFORMANCE -> listOf(Anime4kRestoreEffect(loadRestoreS()))
        QUALITY -> qualityEffects(videoWidth, videoHeight, surfaceWidth, surfaceHeight)
        else -> emptyList()
    }

    private suspend fun qualityEffects(
        videoWidth: Int,
        videoHeight: Int,
        surfaceWidth: Int,
        surfaceHeight: Int,
    ): List<GlEffect> {
        val restore = Anime4kRestoreEffect(loadRestoreM())
        // 分辨率门控（与 mpv 双端同规则）：渲染面相对片源没有放大时，
        // x2 链纯烧 GPU，只做同尺寸还原。尺寸未知时不门控（宁可多花算力）。
        if (!needsUpscale(videoWidth, videoHeight, surfaceWidth, surfaceHeight)) {
            return listOf(restore)
        }
        val chain = mutableListOf<GlEffect>(restore, Anime4kUpscaleEffect(loadUpscaleM()))
        // 视口已知才接 scaler 落地；未知时由 Media3 把 2x 输出缩到渲染面。
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            chain += FitLanczosScalerEffect(surfaceWidth, surfaceHeight)
        }
        return chain
    }

    internal fun needsUpscale(videoWidth: Int, videoHeight: Int, surfaceWidth: Int, surfaceHeight: Int): Boolean {
        if (videoWidth <= 0 || videoHeight <= 0 || surfaceWidth <= 0 || surfaceHeight <= 0) return true
        return minOf(
            surfaceWidth.toDouble() / videoWidth,
            surfaceHeight.toDouble() / videoHeight,
        ) > 1.0
    }
}
