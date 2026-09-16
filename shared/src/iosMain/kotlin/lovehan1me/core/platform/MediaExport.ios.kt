package lovehan1me.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * iOS：写 `Documents/LoveHan1me/{gifs|screenshots}/`。
 *
 * 选 Documents 而不是 tmp 的理由：`iosApp/Info.plist` 已开 **`UIFileSharingEnabled` +
 * `LSSupportsOpeningDocumentsInPlace`**（为备份导入导出而设），所以 Documents 下的产物
 * 会直接出现在「文件」App 里 —— 用户能自己查看/转发，等价于另两端的"保存到可见位置"。
 *
 * 分享面板（`UIActivityViewController`）本项目**尚未接入**（既有先例：`rememberShareText`
 * 在 iOS 也是降级实现），故这里返回 [MediaExportOutcome.SavedOnly] 并提示位置。
 * **这不是失败**，是当前平台能力的如实反映。
 *
 * 落盘复用 `AvatarImageIo.ios.kt` 的 `writeBytesAtPath`（同包 internal）
 * —— 那条路径已被 CI 的 macos runner 编译验证过，不自己新写 NSData 转换。
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun exportMediaAndShare(
    bytes: ByteArray,
    fileName: String,
    mimeType: String,
): MediaExportOutcome {
    val subDir = if (mimeType == "image/gif") "gifs" else "screenshots"
    return try {
        val documents = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true,
        ).first() as? String ?: return MediaExportOutcome.Failed("拿不到 Documents 目录")

        val dir = "$documents/LoveHan1me/$subDir"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )

        if (!writeBytesAtPath("$dir/$fileName", bytes)) {
            return MediaExportOutcome.Failed("写入文件失败")
        }
        MediaExportOutcome.SavedOnly("「文件」App → 我的 iPhone → LoveHan1me/$subDir")
    } catch (t: Throwable) {
        MediaExportOutcome.Failed(t.message ?: "导出失败")
    }
}
