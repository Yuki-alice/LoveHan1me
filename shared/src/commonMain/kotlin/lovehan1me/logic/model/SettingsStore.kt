package lovehan1me.logic.model

import kotlinx.coroutines.flow.StateFlow

interface SettingsStore {
    val settings: StateFlow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}
