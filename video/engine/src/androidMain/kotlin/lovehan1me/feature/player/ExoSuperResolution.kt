package lovehan1me.feature.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect

// ExoPlayer 内核的视频超分（真 Anime4K CNN 链）。
//
// 档位（与 mpv 侧编号一致，UI 传 0/1/2）：
// - OFF：不挂 effect（空表，不创建 GL 程序）；
// - PERFORMANCE：`Restore_CNN_S`（3 卷积 + 1 合并，同尺寸）；
// - QUALITY：`Restore_CNN_M` + `Upscale_CNN_x2_M`（输出 2 倍尺寸）。
// 两档在**片源与视口尺寸都已知**时都再接一张落地 scaler：CNN 只负责"还原/放大"，
// 把结果按视口重采样到最终尺寸这一步不能省 —— 省了就是让 Surface 自己双线性拉伸，
// 观感与桌面（mpv 的 ewa_lanczossharp presentation）分叉。
//
// 素材：`composeResources/files/shaders/` 下的 `.glsl`（mpv 格式），
// 运行时切分 + 模板注入（见 ExoAnime4kPasses / ExoAnime4kPrograms）。
// 失败语义不变：GL 编译/资产异常一律抛给引擎，由它降级 OFF（绝不崩播放）。
object ExoSuperResolution {
    const val OFF = 0
    const val PERFORMANCE = 1
    const val QUALITY = 2

    /**
     * 该档位需要挂的 effects；OFF 返回空表（= 不动原管线）。
     *
     * 读资产是挂起调用（有内存缓存），调用方须在协程内。
     * 尺寸未知（`<=0`）时不挂 scaler：那时连目标分辨率都不知道，挂上去只会把画面缩成 1x1，
     * 交给 Media3 按 Surface 尺寸拉伸是此时唯一正确的行为。
     */
    suspend fun effectsFor(
        level: Int,
        videoWidth: Int,
        videoHeight: Int,
        surfaceWidth: Int,
        surfaceHeight: Int,
    ): List<GlEffect> {
        val chain = when (level.coerceIn(OFF, QUALITY)) {
            PERFORMANCE -> mutableListOf<GlEffect>(Anime4kRestoreEffect(loadRestoreS()))
            QUALITY -> mutableListOf(
                Anime4kRestoreEffect(loadRestoreM()),
                Anime4kUpscaleEffect(loadUpscaleM()),
            )

            else -> return emptyList()
        }
        if (videoWidth > 0 && videoHeight > 0 && surfaceWidth > 0 && surfaceHeight > 0) {
            val (vertex, fragment) = loadScalerSources()
            chain += LanczosSharpScalerEffect(vertex, fragment, surfaceWidth, surfaceHeight)
        }
        return chain
    }
}
