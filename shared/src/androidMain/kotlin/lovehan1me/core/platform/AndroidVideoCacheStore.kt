package lovehan1me.core.platform

import lovehan1me.HanimeCacheManager
import lovehan1me.core.domain.model.HanimeVideo
import lovehan1me.core.util.application
import kotlinx.coroutines.flow.Flow

object AndroidVideoCacheStore : VideoCacheStore {
    override fun load(videoCode: String): Flow<HanimeVideo?> =
        HanimeCacheManager.loadHanimeVideoInfo(application, videoCode)
}
