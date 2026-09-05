package io.github.daisukikaffuchino.han1meviewer.logic.platform

import kotlinx.coroutines.flow.Flow

interface DownloadWorkController {
    fun prune()
    suspend fun initialize()
    fun runningCount(): Flow<Int>

    /** P6d-4E：备份恢复后同步下载并发数上限（Android 转发 HanimeDownloadManager，其余平台 no-op） */
    fun updateDownloadLimit(count: Int) {}
}
