package lovehan1me.feature.home.homepage

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import lovehan1me.core.platform.saveImageToPhotoLibrary
import lovehan1me.data.network.HanimeImageHeaders

// iOS 真实现（Gate4-平台能力补齐）：Darwin 拉字节 → 相册。
//
// 与图片管线同栈：公告图是 hanime1 CDN 直链，同样会被 SNI 阻断，
// 故装 HanimeImageHeaders（ECH 改写，判定收敛到 EchGatePolicy）。
// 相簿写入复用 core.platform 里截图导出同一 helper（授权 → 主线程 UIImageWriteToSavedPhotosAlbum）。
// 失败语义与 android/desktop 一致：任何异常/无授权/解码失败 → false（调用方据此报 toast）。
actual suspend fun saveImageToGallery(imageUrl: String): Boolean {
    val client = HttpClient(Darwin) { install(HanimeImageHeaders) }
    return try {
        saveImageToPhotoLibrary(client.get(imageUrl).bodyAsBytes())
    } catch (_: Exception) {
        false
    } finally {
        client.close()
    }
}