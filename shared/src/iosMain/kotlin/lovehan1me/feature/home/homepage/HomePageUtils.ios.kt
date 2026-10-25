package lovehan1me.feature.home.homepage

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import lovehan1me.core.platform.saveImageToPhotoLibrary
import lovehan1me.data.network.createIosTransport
import lovehan1me.data.network.egress.EgressPurpose
import lovehan1me.core.util.PlatformLock
import lovehan1me.core.util.withLock

// iOS 真实现（Gate4-平台能力补齐）：Darwin 拉字节 → 相册。
//
// 与图片管线同栈：公告图是 hanime1 CDN 直链，同样会被 SNI 阻断，
// 故装网关插件（ECH 改写 + 失败回退，判定收敛到 EgressPlanner）。
// 相簿写入复用 core.platform 里截图导出同一 helper（授权 → 主线程 UIImageWriteToSavedPhotosAlbum）。
// 失败语义与 android/desktop 一致：任何异常/无授权/解码失败 → false（调用方据此报 toast）。
//
// 网络优化 1.1：client 改**进程级单例**——此前每次调用新建/close，公告图批量存相册时
// 一张图一个 TLS 栈；现在随单例只建一次，预算走图片链同款（30s call 档 + 同谓词重试）。

private val albumClientLock = PlatformLock()

private var albumClient: HttpClient? = null

private fun albumClientOrNull(): HttpClient = albumClientLock.withLock {
    albumClient ?: createIosTransport(
        purpose = EgressPurpose.Image,
        withCookies = false,
        requestTimeoutMillis = 30_000,
        retry = true,
    ).also { albumClient = it }
}

actual suspend fun saveImageToGallery(imageUrl: String): Boolean {
    return try {
        saveImageToPhotoLibrary(albumClientOrNull().get(imageUrl).bodyAsBytes())
    } catch (_: Exception) {
        false
    }
}
