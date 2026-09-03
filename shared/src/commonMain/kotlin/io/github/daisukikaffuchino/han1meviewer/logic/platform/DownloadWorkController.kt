package io.github.daisukikaffuchino.han1meviewer.logic.platform

import kotlinx.coroutines.flow.Flow

interface DownloadWorkController {
    fun prune()
    suspend fun initialize()
    fun runningCount(): Flow<Int>
}
