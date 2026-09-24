package lovehan1me.core.domain.model

// 播放相关模型（Gate3-P1 从 AppSettings.kt 抽出，包名不变，全仓 import 零改动）。
// AppSettings 持久化它们（见 :shared 同包文件），定义住在这里断掉 core→data 的方向问题。

enum class PlayerKernel(val value: String) {
    MediaPlayer("MediaPlayer"), ExoPlayer("ExoPlayer"), MpvPlayer("MpvPlayer");

    companion object {
        fun fromValue(value: String): PlayerKernel = entries.firstOrNull { it.value == value } ?: ExoPlayer
        fun fromPreference(value: String): PlayerKernel = fromValue(value)
    }
}

// 画面比例：Fit 留黑边（默认）；Stretch 强行拉伸（将就看完的出口）；Crop 裁掉边缘。
// 并非每端都支持全部三档（Exo 没有拉伸），引擎按 supportedAspectModes 如实声明。
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
// 三端里只有 mpv 内核真的支持，不做假开关。
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
