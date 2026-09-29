package lovehan1me.feature.home.homepage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lovehan1me.data.network.createCdnFetchClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

// 桌面：~/.lovehan1me/pictures/ 写 JPEG（与 datastore 同根目录，便于用户查找）。
actual suspend fun saveImageToGallery(imageUrl: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val request = Request.Builder().url(imageUrl).get().build()
        val bytes = galleryClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching false
            response.body.bytes()
        }
        val image = ByteArrayInputStream(bytes).use { ImageIO.read(it) } ?: return@runCatching false
        val dir = File(File(System.getProperty("user.home"), ".lovehan1me"), "pictures")
        if (!dir.exists()) dir.mkdirs()
        ImageIO.write(image, "JPEG", File(dir, "IMG_${System.currentTimeMillis()}.jpg"))
        true
    }.getOrDefault(false)
}

/**
 * 与首页封面同一条出口：图片同样在 CDN 上，也会被 SNI 阻断。
 *
 * 此前这里是裸 `HttpClient(OkHttp)`——不带 DNS 覆盖、不带代理选择器、不带网关改写，
 * 于是开着 ECH 网关或手填代理时"保存图片"必然失败，而同一张图在页面上显示得好好的。
 */
private val galleryClient by lazy { createCdnFetchClient() }
