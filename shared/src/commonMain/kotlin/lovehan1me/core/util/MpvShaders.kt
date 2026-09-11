package lovehan1me.core.util

/**
 * 阶段一②：视频超分（Anime4K）档位定义与 shader 落盘入口。
 *
 * shader 本体放在 `commonMain/composeResources/files/shaders/`，三端共用；
 * mpv 只接受**磁盘路径**（`change-list glsl-shaders`），所以各端要把它们
 * 落到自己可写的目录再拼成 `:` 分隔的绝对路径字符串。
 *
 * 档位：
 * - [OFF]：不挂任何 shader
 * - [PERFORMANCE]：`Clamp_Highlights + Restore_CNN_S + Upscale_CNN_x2_S`（轻量）
 * - [QUALITY]：`Clamp_Highlights + Restore_CNN_VL + Upscale_CNN_x2_VL +
 *   AutoDownscalePre_x2 + Upscale_CNN_x2_M`（保真优先，GPU 开销大）
 */
object MpvShaders {

    const val OFF = 0
    const val PERFORMANCE = 1
    const val QUALITY = 2

    private val PERFORMANCE_SHADERS = arrayOf(
        "Anime4K_Clamp_Highlights.glsl",
        "Anime4K_Restore_CNN_S.glsl",
        "Anime4K_Upscale_CNN_x2_S.glsl",
    )

    private val QUALITY_SHADERS = arrayOf(
        "Anime4K_Clamp_Highlights.glsl",
        "Anime4K_Restore_CNN_VL.glsl",
        "Anime4K_Upscale_CNN_x2_VL.glsl",
        "Anime4K_AutoDownscalePre_x2.glsl",
        "Anime4K_Upscale_CNN_x2_M.glsl",
    )

    /** 该档位需要的 shader 文件名（相对 `composeResources/files/shaders/`）。 */
    fun fileNames(level: Int): Array<String> = when (level) {
        PERFORMANCE -> PERFORMANCE_SHADERS
        QUALITY -> QUALITY_SHADERS
        else -> emptyArray()
    }
}

/**
 * 把 [level] 档位的 shader 落盘并返回 mpv 需要的 `:` 分隔绝对路径。
 *
 * - [MpvShaders.OFF] 返回空串（表示清空 shader 列表）
 * - 平台不支持或落盘失败返回 `null`，调用方**必须**降级到 OFF（不能抛异常——
 *   animeko 的短板就是 shader 失败会直接抛 `VideoFrameProcessingException`）
 */
internal expect suspend fun materializeMpvShaders(level: Int): String?

/** 各端可写、且 mpv 进程可读的 shader 目录；`null` 表示该平台不支持超分。 */
internal expect suspend fun mpvShaderTargetDir(): String?
