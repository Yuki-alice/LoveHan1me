package lovehan1me.video.contract

// 播放领域的纯值模型。
//
// 为什么定义在契约层而不是引擎层：AppSettings 要持久化它们（:shared 侧），
// 而引擎实现不能被 core 反向依赖。放在这里，持久化侧与引擎侧都只依赖契约。

enum class PlayerKernel(val value: String) {
    MediaPlayer("MediaPlayer"), ExoPlayer("ExoPlayer"), MpvPlayer("MpvPlayer");

    companion object {
        fun fromValue(value: String): PlayerKernel = entries.firstOrNull { it.value == value } ?: ExoPlayer
        fun fromPreference(value: String): PlayerKernel = fromValue(value)
    }
}

// 画面比例三档。具体某一端支持哪几档由引擎按能力声明（见
// `PlaybackEngine.supportedAspectModes()`），不在这里预设
// （曾经写死"Exo 没有拉伸"，与 0.5.0 的实测不符，已删）。
enum class VideoAspectMode(val value: String) {
    Fit("fit"),
    Stretch("stretch"),
    Crop("crop");

    companion object {
        fun fromValue(value: String): VideoAspectMode =
            entries.firstOrNull { it.value == value } ?: Fit
    }
}

// 画面调节三件套（亮度 / 对比度 / 饱和度），单位与 mpv 对齐：-100 ~ 100，0 = 原始。
data class PictureAdjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
) {
    val isNeutral: Boolean get() = brightness == 0f && contrast == 0f && saturation == 0f

    companion object {
        const val MIN = -100f
        const val MAX = 100f
        val Neutral = PictureAdjust()

        fun clamp(value: Float): Float = value.coerceIn(MIN, MAX)
    }
}

// 一部片子的一个可选源。UI 侧只消费 label，uri/headers/mimeType 是给引擎开流用的。
data class PlaybackQuality(
    val label: String,
    val uri: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
)

// 播放默认值：速度档表 + 手势灵敏度。
//
// 为什么在契约层：UI 拿它当默认参数（控件签名里就要用），引擎拿它当兜底，而引擎实现不能被 UI 依赖。
object PlayerDefaults {
    const val DEFAULT_SPEED = 1f
    const val DEFAULT_SPEED_INDEX = 2
    /**
     * 进度滑动手势灵敏度：进度增量 = `拖动位移 / (屏宽 × 本值)`，所以**数值越大越迟钝**。
     *
     * 2.25f 即满幅横滑走约 44% 进度 —— 原设置页默认档（4 档）的实际手感，
     * 设置项删掉后把这个值冻在这里，三端唯一来源。
     */
    const val PROGRESS_SLIDE_SENSITIVITY = 2.25f
    const val DEFAULT_LONG_PRESS_SPEED_MULTIPLIER = 2.5f
    val speeds = floatArrayOf(
        0.5f,
        0.75f,
        DEFAULT_SPEED,
        1.25f,
        1.5f,
        1.75f,
        2f,
        2.25f,
        2.5f,
        2.75f,
        3f,
    )

    val speedLabels: Array<String>
        get() = Array(speeds.size) { "${speeds[it]}x" }
}
