package io.github.daisukikaffuchino.han1meviewer.logic.platform

import androidx.work.WorkManager
import io.github.daisukikaffuchino.han1meviewer.worker.HanimeDownloadManager
import io.github.daisukikaffuchino.han1meviewer.worker.HanimeDownloadWorker
import io.github.daisukikaffuchino.utils.application
import kotlinx.coroutines.flow.Flow

object AndroidDownloadWorkController : DownloadWorkController {
    override fun prune() {
        WorkManager.getInstance(application).pruneWork()
    }

    override suspend fun initialize() {
        HanimeDownloadManager.init()
    }

    override fun runningCount(): Flow<Int> =
        HanimeDownloadWorker.getRunningWorkInfoCount(application)
}
