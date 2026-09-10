package lovehan1me.ui.screen.home.homepage

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import lovehan1me.logic.dao.Han1meDatabaseContext

// Android：:app 原 saveImageToGallery 的 MediaStore 逻辑照搬（context 走共享 holder）。
// toast 移到调用方（Boolean 驱动），本函数只返回是否成功。
// P6d-2：原 SingletonImageLoader（:app 注册的单例）改为直建 ImageLoader
// （coil 单例 artifact 在 shared 不可见；单次保存无需复用缓存，行为等价）。
actual suspend fun saveImageToGallery(imageUrl: String): Boolean {
    val context = Han1meDatabaseContext.appContext
    return try {
        val loader = ImageLoader.Builder(context).build()
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .build()
        val result = (loader.execute(request) as? SuccessResult)?.image
        val bitmap = result?.toBitmap() ?: return false
        val filename = "IMG_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        val fos = uri.let { context.contentResolver.openOutputStream(it) } ?: return false
        fos.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        true
    } catch (_: Exception) {
        false
    }
}
