package lovehan1me.feature.home.homepage

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import lovehan1me.data.database.dao.Han1meDatabaseContext
import lovehan1me.data.network.createCdnFetchClient

// Android：:app 原 saveImageToGallery 的 MediaStore 逻辑照搬（context 走共享 holder）。
// toast 移到调用方（Boolean 驱动），本函数只返回是否成功。
//
// 出口与首页封面/下载同一条：保存的是 CDN 上的原图（`vdownload.hembed.com/image/…`），
// 同样会被 SNI 阻断。此前这里是**裸 `ImageLoader`**（只换掉了 :app 的 SingletonImageLoader），
// 不带 DNS 兜底 / 代理选择器 / 网关改写 —— 于是开着 ECH 网关或手填代理时"保存图片"必然失败，
// 而同一张图在页面上显示得好好的。配置统一收在 createCdnFetchClient（见 CdnFetchClientTest）。
actual suspend fun saveImageToGallery(imageUrl: String): Boolean {
    val context = Han1meDatabaseContext.appContext
    return try {
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .build()
        val result = (galleryLoader.execute(request) as? SuccessResult)?.image
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

/**
 * 常驻的保存用加载器（不是每次保存新建一个）。
 *
 * 与 `rememberHanimeImageLoader` 的取图链同源：`createCdnFetchClient()` 里带着
 * `HanimeDns` / `HanimeProxySelector` / `EchGateInterceptor`，所以保存图片与浏览、
 * 下载走的是同一条出口；共享同一个连接池与 ImageLoader 缓存，也不再有"每次保存
 * 各开一个连接池"的浪费。
 */
private val galleryLoader: ImageLoader by lazy {
    val context = Han1meDatabaseContext.appContext
    ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { createCdnFetchClient() }))
        }
        .build()
}
