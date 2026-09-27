package lovehan1me.video.contract

import kotlinx.coroutines.flow.StateFlow

/**
 * 超分档位的规范取值域（0=关 / 1=效率 / 2=质量，三端一致）。
 *
 * 这里的常量是**契约**而不是某个引擎的实现细节：UI 要按索引取文案，
 * 落盘也按同一索引存 —— 换个引擎/换个端之后同一个数值含义不变。
 */
object VideoEnhancementLevels {
    const val OFF = 0
    const val PERFORMANCE = 1
    const val QUALITY = 2

    /** 升序全档位；[VideoEnhancementController.levels] 的默认取值。 */
    val ALL: List<Int> = listOf(OFF, PERFORMANCE, QUALITY)
}

/**
 * 视频增强（当前即超分）控制器。
 *
 * 与从前的 `PlaybackEngine.setSuperResolution` / `supportsSuperResolution` 的区别：
 * 它是**独立的能力对象**，不支持超分的端直接给 null，调用方（UI）整块隐藏入口，
 * 而不是拿到一个"调用了但什么都没发生"的空实现 —— 后者让"平台没做"和
 * "平台做了但返回 false"长得一样，调用方分不出来。
 *
 * 实现方约束：**不得因增强失败中断播放** —— 档位不可用时自行降级到可用档位
 * （QUALITY → PERFORMANCE → OFF），返回值如实反映最终生效的档位。
 */
interface VideoEnhancementController {

    /** 可选档位索引（含 [VideoEnhancementLevels.OFF]），升序；UI 据此决定菜单项。 */
    val levels: List<Int>

    /**
     * 用户选定的档位。
     *
     * 是流而不是快照：它会在用户没点菜单的情况下自己变 —— shader 落盘失败、mpv/GL
     * 拒绝挂载，引擎把这一档收回（通常是收到 [VideoEnhancementLevels.OFF]），
     * UI 订阅它才不会显示一个"选中了但其实挂不上"的假状态。
     *
     * 但**分辨率门控不改它**：片源已经够大、这一帧不需要放大时，档位照旧保留，
     * 只是暂时不挂。把门控结果写进流会让菜单在用户点下去的同一帧弹回「关闭」，
     * 看上去等于按钮坏了。
     */
    val level: StateFlow<Int>

    /**
     * 切到 [level]。
     *
     * @return 这一帧**真正挂上**的档位。与入参不同有两种成因，处理方式不一样：
     *         分辨率门控（片源不需要放大）只是暂时不挂，[level] 保持入参；
     *         挂不上（shader 缺失、后端拒绝）才会把 [level] 一起收回。
     */
    suspend fun setLevel(level: Int): Int
}

/**
 * 把落盘/传入的档位收敛到 [levels] 允许的取值。
 *
 * 读侧回落：本键是新增的，老存档里没有它，读到默认 [VideoEnhancementLevels.OFF]，
 * 与升级前"每次进页面都是关"的语义一致；存档里的值超出当前引擎可选档位
 * （换了引擎）或被写坏（负数/越界）时同样降到 OFF，不把无效索引交给引擎。
 */
fun resolveEnhancementLevel(stored: Int, levels: List<Int>): Int =
    if (stored in levels) stored else VideoEnhancementLevels.OFF