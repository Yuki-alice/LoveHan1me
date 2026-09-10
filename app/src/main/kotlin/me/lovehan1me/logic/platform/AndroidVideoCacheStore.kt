package me.lovehan1me.logic.platform

import me.lovehan1me.HCacheManager
import me.lovehan1me.logic.model.HanimeVideo
import me.lovehan1me.utils.application
import kotlinx.coroutines.flow.Flow

object AndroidVideoCacheStore : VideoCacheStore {
    override fun load(videoCode: String): Flow<HanimeVideo?> =
        HCacheManager.loadHanimeVideoInfo(application, videoCode)
}
