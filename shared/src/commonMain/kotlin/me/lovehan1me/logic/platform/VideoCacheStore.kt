package me.lovehan1me.logic.platform

import me.lovehan1me.logic.model.HanimeVideo
import kotlinx.coroutines.flow.Flow

interface VideoCacheStore {
    fun load(videoCode: String): Flow<HanimeVideo?>
}
