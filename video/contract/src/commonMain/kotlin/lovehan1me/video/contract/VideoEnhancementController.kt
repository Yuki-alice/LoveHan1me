package lovehan1me.video.contract

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
     * 当前**生效**档位（不是请求值）。
     *
     * 与 [setLevel] 的返回值同源：降级（QUALITY 不可用 → PERFORMANCE → OFF）后
     * 这里读到的也是降级后的档位，UI 才不会显示一个"选中了但其实没生效"的假状态。
     */
    val level: Int

    /**
     * 切到 [level]。
     *
     * @return **实际生效**的档位。与入参不同即表示发生了降级，调用方应据此刷新显示。
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