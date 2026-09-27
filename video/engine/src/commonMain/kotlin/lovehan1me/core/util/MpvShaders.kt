package lovehan1me.core.util

/**
 * 视频超分（Anime4K）的 mpv 侧档位定义与 shader 素材清单。
 *
 * shader 本体放在 `commonMain/composeResources/files/shaders/`；mpv 只接受**磁盘路径**
 * （`change-list glsl-shaders append <单个路径>`），所以要用它的端要把文件
 * 落到自己可写的目录，再按档位顺序逐个 append。
 *
 * 档位的 pass 链（顺序即执行顺序，写反了会把放大后的图再喂给还原链）：
 * - [OFF]：不挂任何 shader
 * - [PERFORMANCE]：`Restore_CNN_S` —— 单条同尺寸还原，最省的一档
 * - [QUALITY]：`Clamp_Highlights → Restore_CNN_VL → Upscale_CNN_x2_VL →
 *   AutoDownscalePre_x2 → AutoDownscalePre_x4 → Upscale_CNN_x2_M`
 *   —— 先压高光防过曝，VL 还原 + x2 放大到 4 倍，再由两段 AutoDownscalePre
 *   按目标尺寸把放大倍数收回来（x2 / x4 各管一半），最后 M 档补一次细节。
 *
 * ⚠️ mpv 的列表属性用**逗号**分隔，`:` 或 `;` 拼成的整串会被当成一个路径。
 * 所以这里给的是 `List<String>` 而不是拼好的字符串，逐条 append 才正确。
 */
object MpvShaders {

    const val OFF = 0
    const val PERFORMANCE = 1
    const val QUALITY = 2

    private val PERFORMANCE_SHADERS = listOf(
        "Anime4K_Restore_CNN_S.glsl",
    )

    private val QUALITY_SHADERS = listOf(
        "Anime4K_Clamp_Highlights.glsl",
        "Anime4K_Restore_CNN_VL.glsl",
        "Anime4K_Upscale_CNN_x2_VL.glsl",
        "Anime4K_AutoDownscalePre_x2.glsl",
        "Anime4K_AutoDownscalePre_x4.glsl",
        "Anime4K_Upscale_CNN_x2_M.glsl",
    )

    /** 该档位需要的 shader 文件名（相对 `composeResources/files/shaders/`），顺序即挂载顺序。 */
    fun fileNames(level: Int): List<String> = when (level) {
        PERFORMANCE -> PERFORMANCE_SHADERS
        QUALITY -> QUALITY_SHADERS
        else -> emptyList()
    }
}

/**
 * 把 [level] 档位的 shader 落盘并返回**按挂载顺序排列的绝对路径**。
 *
 * - [MpvShaders.OFF] 返回空表（调用方据此清掉已挂的 shader）
 * - 平台不支持或落盘失败返回 `null`，调用方**必须**降级而不是抛异常——
 *   超分把播放搞挂是不可接受的失效方式
 */
expect suspend fun materializeMpvShaders(level: Int): List<String>?

/** 各端可写、且 mpv 进程可读的 shader 目录；`null` 表示该平台不支持超分。 */
expect suspend fun mpvShaderTargetDir(): String?
