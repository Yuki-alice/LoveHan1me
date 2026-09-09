package io.github.daisukikaffuchino.han1meviewer.logic.platform

import androidx.work.WorkManager
import io.github.daisukikaffuchino.han1meviewer.logic.dao.Han1meDatabases
import io.github.daisukikaffuchino.han1meviewer.logic.entity.download.HanimeDownloadEntity
import io.github.daisukikaffuchino.han1meviewer.util.SafFileManager
import io.github.daisukikaffuchino.han1meviewer.worker.HanimeDownloadManager
import io.github.daisukikaffuchino.han1meviewer.worker.HanimeDownloadWorker
import io.github.daisukikaffuchino.utils.application
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
