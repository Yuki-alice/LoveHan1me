package io.github.daisukikaffuchino.han1meviewer.logic.platform

import io.github.daisukikaffuchino.han1meviewer.HCacheManager
import io.github.daisukikaffuchino.han1meviewer.logic.model.HanimeVideo
import io.github.daisukikaffuchino.utils.application
import kotlinx.coroutines.flow.Flow

object AndroidVideoCacheStore : VideoCacheStore {
    override fun load(videoCode: String): Flow<HanimeVideo?> =
        HCacheManager.loadHanimeVideoInfo(application, videoCode)
}
