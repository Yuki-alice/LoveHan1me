package lovehan1me.core.platform

import lovehan1me.core.domain.model.HanimeVideo
import kotlinx.coroutines.flow.Flow

interface VideoCacheStore {
    fun load(videoCode: String): Flow<HanimeVideo?>
}
