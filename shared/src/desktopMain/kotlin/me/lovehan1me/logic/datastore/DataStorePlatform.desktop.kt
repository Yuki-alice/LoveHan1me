package me.lovehan1me.logic.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

// Desktop：全新落盘，无历史 SharedPreferences 可迁
internal actual fun platformPreferenceMigrations(): List<DataMigration<Preferences>> = emptyList()

internal actual fun <T> runBlockingIo(block: suspend CoroutineScope.() -> T): T =
    runBlocking(Dispatchers.IO, block)

private val initLock = Any()

internal actual fun <T> withInitLock(block: () -> T): T = synchronized(initLock) { block() }
