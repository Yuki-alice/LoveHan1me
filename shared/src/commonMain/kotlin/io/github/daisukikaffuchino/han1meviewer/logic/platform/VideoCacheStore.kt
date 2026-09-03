package io.github.daisukikaffuchino.han1meviewer.logic.platform

import io.github.daisukikaffuchino.han1meviewer.logic.model.HanimeVideo
import kotlinx.coroutines.flow.Flow

interface VideoCacheStore {
    fun load(videoCode: String): Flow<HanimeVideo?>
}
