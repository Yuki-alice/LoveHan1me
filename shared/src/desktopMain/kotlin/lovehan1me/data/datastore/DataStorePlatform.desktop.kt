package lovehan1me.data.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences

// Desktop：全新落盘，无历史 SharedPreferences 可迁
internal actual fun platformPreferenceMigrations(): List<DataMigration<Preferences>> = emptyList()

private val initLock = Any()

internal actual fun <T> withInitLock(block: () -> T): T = synchronized(initLock) { block() }
