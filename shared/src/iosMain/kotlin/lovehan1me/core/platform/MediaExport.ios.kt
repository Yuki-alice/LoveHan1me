package lovehan1me.core.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIImage
import platform.UIKit.UIImageWriteToSavedPhotosAlbum
import kotlin.coroutines.resume

/**
 * iOS：截图进相簿 + 写 `Documents/LoveHan1me/{gifs|screenshots}/` 双落点；
 * GIF 只写 Documents（相簿存 GIF 只留首帧，无意义）。
 *
 * 相簿需要 `NSPhotoLibraryUsageDescription`（Info.plist 已配，传统鉴权弹窗用它）：
 * 首次保存弹系统授权，拒绝则静默回落 Documents（以鉴权结果为准報成功，不以"调了 API"为准）
 *
 * 选 Documents 保留而不删的理由：`iosApp/Info.plist` 已开 **`UIFileSharingEnabled` +
 * `LSSupportsOpeningDocumentsInPlace`**，Documents 下的产物会直接出现在「文件」App 里，
 * 是相簿之外的第二可见位置。
 *
 * 分享面板（`UIActivityViewController`）本项目**尚未接入**，故这里返回
 * [MediaExportOutcome.SavedOnly] 并提示位置。**这不是失败**，是当前平台能力的如实反映。
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
        val documentsHint = "「文件」App → 我的 iPhone → LoveHan1me/$subDir"
        // Gate4-1：截图追加相簿落点。GIF 跳过（相簿只留首帧）。
        if (mimeType != "image/gif" && saveImageToPhotoLibrary(bytes)) {
            return MediaExportOutcome.SavedOnly("相簿（「文件」App 亦有一份）")
        }
        MediaExportOutcome.SavedOnly(documentsHint)
    } catch (t: Throwable) {
        MediaExportOutcome.Failed(t.message ?: "导出失败")
    }
}

// 相簿写入须在主线程（UIKit 约束）。成功判据是**事先拿到授权**而不是事后回调：
@OptIn(ExperimentalForeignApi::class)
private suspend fun saveImageToPhotoLibrary(bytes: ByteArray): Boolean {
    if (bytes.isEmpty()) return false
    return withContext(Dispatchers.Main) {
        val authorized = suspendCancellableCoroutine { continuation ->
            PHPhotoLibrary.requestAuthorization { status ->
                continuation.resume(status == PHAuthorizationStatusAuthorized)
            }
        }
        if (!authorized) return@withContext false
        val image = bytes.toUIImage() ?: return@withContext false
        UIImageWriteToSavedPhotosAlbum(image, null, null, null)
        true
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toUIImage(): UIImage? {
    if (isEmpty()) return null
    val data: NSData = usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
    return UIImage.imageWithData(data)
}
