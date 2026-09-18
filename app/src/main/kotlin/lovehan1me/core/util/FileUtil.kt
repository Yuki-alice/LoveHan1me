package lovehan1me.core.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import lovehan1me.FILE_PROVIDER_AUTHORITY
import java.io.File

// E1 收尾清理说明：
// 本文件原有 5 个成员，其中 3 个已确认为死代码并删除（全仓 grep 零引用，
// 且 shared 侧各有替代实现）：
//   - `File?.folderSize`        → shared `HomePlatformActions.getCacheDirSize()`
//   - `Drawable.saveTo()`       → 唯一调用点是封面写盘，已改为 `CoverImageFetcher` 直写字节
//   - `loadAssetAs<T>()`        → shared `ComposeAssetsSync.decodeComposeAsset`
// 保留下来的两个仍在使用，见各自调用点。

fun File.createFileIfNotExists(): Boolean {
    return if (!exists()) {
        parentFile?.mkdirs()
        createNewFile()
    } else {
        isFile
    }
}

/**
 * Validates a downloaded video and returns a shareable URI.
 */
fun Context.getDownloadedHanimeVideoUri(
    uri: String,
    onFileNotFound: (() -> Unit)? = null,
): Uri? {
    val videoUri = uri.toUri()
    if (videoUri.scheme == ContentResolver.SCHEME_CONTENT) {
        try {
            contentResolver.openFileDescriptor(videoUri, "r")?.use { pfd ->
                if (pfd.statSize <= 0) {
                    onFileNotFound?.invoke()
                    return null
                }
            }
        } catch (_: Exception) {
            onFileNotFound?.invoke()
            return null
        }
        return videoUri
    }

    val videoFile = File(videoUri.path ?: "")
    if (!videoFile.exists()) {
        onFileNotFound?.invoke()
        return null
    }
    return FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, videoFile)
}
