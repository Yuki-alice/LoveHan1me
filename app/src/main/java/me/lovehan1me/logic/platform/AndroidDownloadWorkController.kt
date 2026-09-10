package me.lovehan1me.logic.platform

import androidx.work.WorkManager
import me.lovehan1me.logic.dao.Han1meDatabases
import me.lovehan1me.logic.model.HanimeVideo
import me.lovehan1me.logic.entity.download.HanimeDownloadEntity
import me.lovehan1me.util.SafFileManager
import me.lovehan1me.worker.HanimeDownloadManager
import me.lovehan1me.worker.HanimeDownloadWorker
import me.lovehan1me.utils.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** M6：Android 引擎 = WorkManager 转发；SAF 导入经 CurrentActivityHolder 取 Context。 */
object AndroidDownloadWorkController : DownloadWorkController {
    override fun prune() {
        WorkManager.getInstance(application).pruneWork()
    }

    override suspend fun initialize() {
        HanimeDownloadManager.init()
    }

    override fun runningCount(): Flow<Int> =
        HanimeDownloadWorker.getRunningWorkInfoCount(application)

    override fun updateDownloadLimit(count: Int) {
        HanimeDownloadManager.maxConcurrentDownloadCount = count
    }

    override fun pauseTask(entity: HanimeDownloadEntity) {
        HanimeDownloadManager.stopTask(entity)
    }

    override fun resumeTask(entity: HanimeDownloadEntity) {
        HanimeDownloadManager.resumeTask(entity)
    }

    override fun deleteTask(entity: HanimeDownloadEntity) {
        HanimeDownloadManager.deleteTask(entity)
    }

    override fun deleteVideoFolder(videoCode: String) {
        SafFileManager.deleteDownloadVideoFolder(application, videoCode)
    }

    override suspend fun addTask(
        video: me.lovehan1me.logic.model.HanimeVideo,
        videoCode: String,
        quality: String?,
        groupId: Int,
        redownload: Boolean,
    ) {
        val q = quality ?: video.videoUrls.keys.firstOrNull() ?: return
        HanimeDownloadManager.addTask(
            HanimeDownloadWorker.Args(
                quality = q,
                downloadUrl = video.videoUrls[q]?.link,
                videoType = video.videoUrls[q]?.suffix,
                hanimeName = video.title,
                videoCode = videoCode,
                coverUrl = video.coverUrl,
                groupId = groupId,
            ),
            redownload = redownload,
        )
        // M8-1b：恢复迁移前 VideoRouteActions 的入队即写 info.json（本地播放读它）；
        // 写失败不阻塞入队（读侧有 DB 兜底）。
        runCatching {
            me.lovehan1me.HCacheManager.saveHanimeVideoInfo(
                application, videoCode, video,
            )
        }.onFailure {
            me.lovehan1me.utils.LogUtil.w("AndroidDownload", "save info.json failed: $videoCode", it)
        }
    }

    override suspend fun importDownloaded(): Boolean = withContext(Dispatchers.IO) {
        try {
            val context = CurrentActivityHolder.activity ?: return@withContext false
            if (!SafFileManager.checkSafPermissions(context)) return@withContext false
            SafFileManager.scanAndImportHanimeDownloads(context, Han1meDatabases.download.hanimeDownloadDao)
            true
        } catch (e: Exception) {
            false
        }
    }
}
