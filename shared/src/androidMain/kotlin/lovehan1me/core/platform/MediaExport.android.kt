package lovehan1me.core.platform

import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import lovehan1me.data.database.dao.Han1meDatabaseContext

/**
 * Android：写 **MediaStore**（`Pictures/LoveHan1me/`，用户能在相册里直接看到），
 * 再用 MediaStore 回传的 `content://` URI 唤起分享面板。
 *
 * 几个刻意的选择：
 * - **不走 FileProvider**：MediaStore 的 URI 本身就是可授权的 content URI，
 *   省掉"把文件写到 FileProvider 覆盖的目录再换 URI"这一步（也少一处 file_paths 配置依赖）。
 * - **`IS_PENDING` 两段式写入**（minSdk 29 起可用）：先占位再写内容，
 *   避免相册在文件写完整之前就索引到半截的 GIF。
 * - **没有 Activity 时不硬分享**：从后台（例如下载完成回调）调用时拿不到 Activity，
 *   返回 [MediaExportOutcome.SavedOnly] 让 UI 提示位置，而不是抛 `ActivityNotFoundException`。
 */
actual suspend fun exportMediaAndShare(
    bytes: ByteArray,
    fileName: String,
    mimeType: String,
): MediaExportOutcome {
    val context = Han1meDatabaseContext.appContext
    val locationHint = GALLERY_LOCATION_HINT
    return try {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/LoveHan1me",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: return MediaExportOutcome.Failed("相册拒绝写入（insert 返回 null）")

        val written = runCatching {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } != null
        }.getOrDefault(false)
        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return MediaExportOutcome.Failed("写入相册失败")
        }

        // 清掉 pending，相册才开始索引
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )

        val activity = CurrentActivityHolder.activity
            ?: return MediaExportOutcome.SavedOnly(locationHint)

        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            // clipData + 读权限：Android 11+ 只给 ACTION_SEND 授予 URI 访问要靠这两样
            clipData = ClipData.newRawUri(fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            activity.startActivity(Intent.createChooser(send, null))
            MediaExportOutcome.Shared(locationHint)
        }.getOrElse { MediaExportOutcome.SavedOnly(locationHint) }
    } catch (t: Throwable) {
        MediaExportOutcome.Failed(t.message ?: "导出失败")
    }
}

/** 给用户的落点描述（各端不同，故不放进公共层）。 */
private const val GALLERY_LOCATION_HINT = "相册 → Pictures/LoveHan1me"
