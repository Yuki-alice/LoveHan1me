package lovehan1me.ui.screen.home.homepage

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

// 桌面：~/.han1meviewer/pictures/ 写 JPEG（与 datastore 同根目录，便于用户查找）。
actual suspend fun saveImageToGallery(imageUrl: String): Boolean {
    return try {
        val client = HttpClient(OkHttp)
        val bytes = client.get(imageUrl).bodyAsBytes()
        client.close()
        val image = ByteArrayInputStream(bytes).use { ImageIO.read(it) } ?: return false
        val dir = File(File(System.getProperty("user.home"), ".han1meviewer"), "pictures")
        if (!dir.exists()) dir.mkdirs()
        ImageIO.write(image, "JPEG", File(dir, "IMG_${System.currentTimeMillis()}.jpg"))
        true
    } catch (_: Exception) {
        false
    }
}
