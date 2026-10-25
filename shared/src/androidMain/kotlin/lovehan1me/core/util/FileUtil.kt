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
    if (uri.isBlank()) {
        onFileNotFound?.invoke()
        return null
    }
    // 遗留/跨端占位（桌面/iOS 历史 `local://`）在 Android 侧无对应文件，直接走缺文件分支
    // （调用方弹删库确认框），避免 File("local://…") 误判或 FileProvider 抛异常。
    if (uri.startsWith("local://")) {
        onFileNotFound?.invoke()
        return null
    }
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

    val videoFile = when (videoUri.scheme) {
        null, ContentResolver.SCHEME_FILE -> File(videoUri.path ?: uri)
        // content:// 已在上分支 return；http(s) 等远端串不可能是已下载产物，直接缺文件。
        else -> {
            onFileNotFound?.invoke()
            return null
        }
    }
    if (!videoFile.isFile) {
        onFileNotFound?.invoke()
        return null
    }
    return runCatching {
        FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, videoFile)
    }.getOrElse {
        onFileNotFound?.invoke()
        null
    }
}
