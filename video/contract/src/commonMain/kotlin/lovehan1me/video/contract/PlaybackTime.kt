package lovehan1me.video.contract

/**
 * 把播放位置（毫秒）格式化成 `mm:ss`，超过一小时则 `h:mm:ss`。
 *
 * 负数按 0 处理（拖动预览可能算出负位置）。补零走纯 Kotlin 的 `padStart`，
 * 与 `%02d` 等价且不依赖 JVM。
 */
fun formatPlaybackTime(positionMs: Long): String {
    val totalSeconds = (positionMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}