/*
 * 本文件接口形状移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.contract

/**
 * 系统音量边。实现由各端的宿主提供（Android 走 AudioManager 的 stream，
 * 桌面/iOS 走播放器自身的音量控制），契约层只声明形状。
 */
interface AudioManager {
    /**
     * @return 0..1
     */
    fun getVolume(streamType: StreamType): Float

    /** 该平台在 [streamType] 上能表示的最小音量变化量。 */
    fun getVolumeStep(streamType: StreamType): Float = 0.01f

    fun setVolume(streamType: StreamType, levelPercentage: Float)
}

enum class StreamType {
    MUSIC,
}

/**
 * 屏幕亮度边。不支持调节的端（桌面多屏、部分 iOS 场景）由调用方声明为不支持，
 * 而不是给一个假的实现。
 */
interface BrightnessManager {
    /**
     * @return 0..1
     */
    fun getBrightness(): Float

    fun setBrightness(level: Float)
}
