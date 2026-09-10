package io.github.daisukikaffuchino.han1meviewer.logic.platform

import io.github.daisukikaffuchino.han1meviewer.HanimeResolution
import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.logic.dao.Han1meDatabases
import io.github.daisukikaffuchino.han1meviewer.logic.datastore.DataStoreManager
import io.github.daisukikaffuchino.han1meviewer.logic.entity.download.DownloadGroupEntity
import io.github.daisukikaffuchino.han1meviewer.logic.model.HanimeVideo
import io.github.daisukikaffuchino.han1meviewer.logic.state.DownloadState
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.posix.stat
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * M8-1a：iOS 下载引擎冒烟（对齐 desktopApp DownloadSmoke 的 T1 快速完成）。
 *
 * addTask(小体积 mp4 直链）→ 轮询 DB 至 Finished → 校验落盘文件字节数 == DB 记录
 * → deleteTask 清理。网络受限环境（本机模拟器 test.kexe TLS -1202，见 M7-4）
 * 经 [preflight] 门控后 SKIP，健康网络下全断言。
 */
@OptIn(ExperimentalForeignApi::class)
class IosDownloadSmokeTest {

    private val url = "https://download.samplelib.com/mp4/sample-15s.mp4"
    private val code = "smoke-ios-dl-15s"
    private val quality = "1080P"
    private val title = "Smoke iOS T1 (15s)"

    private suspend fun preflight(): Boolean {
        try {
            val client = HttpClient(Darwin)
            try {
                val resp = withTimeoutOrNull(15_000L) { client.get(url) } ?: return false
                resp.bodyAsText().take(16)
                return resp.status.value in 200..299
            } finally {
                client.close()
            }
        } catch (e: Exception) {
            println("[SKIP] preflight 失败（环境出网受限，非引擎问题）: ${e.message?.take(160)}")
            return false
        }
    }

    private fun fileSizeOrNull(path: String): Long? = memScoped {
        val st = alloc<stat>()
        if (stat(path, st.ptr) != 0) null else st.st_size
    }

    @Test
    fun downloadSmallFileToFinished() = runBlocking {
        if (!preflight()) {
            println("[SKIP] downloadSmallFileToFinished：test.kexe 进程无可用 HTTPS 出网")
            return@runBlocking
        }
        DataStoreManager.initialize()
        runCatching { SettingsRepository.install(DataStoreManager) }

        val db = Han1meDatabases.download
        val dao = db.hanimeDownloadDao
        runCatching { db.downloadGroupDao.insertDefaultGroup() }
        runCatching { dao.delete(code, quality) }

        val video = HanimeVideo(
            title = title,
            coverUrl = "",
            chineseTitle = null,
            introduction = null,
            uploadTime = null,
            videoUrls = HanimeResolution().apply {
                parseResolution(HanimeResolution.RES_1080P, url, "video/mp4")
            }.toResolutionLinkMap(),
            tags = emptyList(),
        )
        IosDownloadWorkController.initialize()
        IosDownloadWorkController.addTask(
            video = video,
            videoCode = code,
            quality = quality,
            groupId = DownloadGroupEntity.DEFAULT_GROUP_ID,
        )

        val done = withTimeoutOrNull(150_000L) {
            while (true) {
                val e = dao.find(code, quality)
                if (e != null && e.state == DownloadState.Finished && e.downloadedLength > 0) {
                    return@withTimeoutOrNull e
                }
                if (e != null && e.state == DownloadState.Failed) return@withTimeoutOrNull null
                delay(500L)
            }
            @Suppress("UNREACHABLE_CODE") null
        }
        assertTrue(done != null, "150s 内未 Finished：state=${dao.find(code, quality)?.state}")
        done!!

        val documents = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true,
        ).first() as String
        val expectedFile = "$documents/Han1meViewer/downloads/$code/$title [$quality].mp4"
        assertTrue(
            NSFileManager.defaultManager.fileExistsAtPath(expectedFile),
            "落盘文件不存在：$expectedFile",
        )
        val size = fileSizeOrNull(expectedFile)
        println("[SMOKE] file=$expectedFile size=$size dbLength=${done.length} downloaded=${done.downloadedLength}")
        assertTrue(size == done.downloadedLength && done.downloadedLength == done.length,
            "文件字节数不一致：file=$size db=${done.downloadedLength}/${done.length}")

        IosDownloadWorkController.deleteTask(done)
        val cleaned = withTimeoutOrNull(10_000L) {
            while (dao.find(code, quality) != null) delay(250L)
            true
        } ?: false
        assertTrue(cleaned, "deleteTask 后 DB 行未清除")
    }
}
