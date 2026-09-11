package lovehan1me.core.util

/**
 * 阶段一⑦：已下载视频的文件名反解（三端共用）。
 *
 * 落盘约定（桌面 `DesktopDownloadWorkController.videoFile` /
 * iOS `IosDownloadWorkController.videoFilePath` 同构）：
 * `<sanitize(title)> [<quality>].<suffix>`
 */
internal object DownloadedVideoName {

    /** @return (title, quality)；不符合落盘命名规则时返回 `null`。 */
    fun parse(name: String): Pair<String, String>? {
        val dot = name.lastIndexOf('.')
        if (dot <= 0) return null
        val stem = name.substring(0, dot)
        val open = stem.lastIndexOf(" [")
        if (open <= 0 || !stem.endsWith("]")) return null
        val quality = stem.substring(open + 2, stem.length - 1)
        if (quality.isBlank()) return null
        return stem.substring(0, open) to quality
    }
}
