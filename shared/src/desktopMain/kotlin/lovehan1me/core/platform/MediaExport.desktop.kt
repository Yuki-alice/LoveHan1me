package lovehan1me.core.platform

import java.awt.Desktop
import java.io.File
import okio.buffer
import okio.sink

/**
 * 桌面：写 `~/.lovehan1me/{gifs|screenshots}/`（与 `~/.lovehan1me/pictures` 同根，
 * 便于用户在一处找到本应用的所有产物），然后**打开所在文件夹**。
 *
 * 桌面没有跨桌面环境的系统分享面板（Linux 上 `Desktop.Action.OPEN` 还常不可用），
 * 所以"打开文件夹"是比"什么都不做"更实用的降级 —— 至少用户立刻能看到产物。
 * 因此这里通常返回 [MediaExportOutcome.SavedOnly]，**这是预期行为，不是失败**。
 */
actual suspend fun exportMediaAndShare(
    bytes: ByteArray,
    fileName: String,
    mimeType: String,
): MediaExportOutcome {
    val subDir = if (mimeType == "image/gif") "gifs" else "screenshots"
    return try {
        val root = File(System.getProperty("user.home"), ".lovehan1me")
        val dir = File(root, subDir)
        if (!dir.exists() && !dir.mkdirs()) {
            return MediaExportOutcome.Failed("无法创建目录：${dir.absolutePath}")
        }
        File(dir, fileName).sink().buffer().use { it.write(bytes) }

        val hint = "~/.lovehan1me/$subDir"
        val opened = runCatching {
            if (Desktop.isDesktopSupported() &&
                Desktop.getDesktop().isSupported(Desktop.Action.OPEN)
            ) {
                Desktop.getDesktop().open(dir)
                true
            } else {
                false
            }
        }.getOrDefault(false)

        MediaExportOutcome.SavedOnly(
            if (opened) "$hint（已打开所在文件夹）" else hint,
        )
    } catch (t: Throwable) {
        MediaExportOutcome.Failed(t.message ?: "导出失败")
    }
}
