package io.github.daisukikaffuchino.han1meviewer.logic.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import io.github.daisukikaffuchino.han1meviewer.logic.dao.Han1meDatabaseContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

// Android：保留原有的两条 SharedPreferences 迁移（default preferences + packageName 命名的那份）
internal actual fun platformPreferenceMigrations(): List<DataMigration<Preferences>> {
    val appContext = Han1meDatabaseContext.appContext
    return listOf(
        SharedPreferencesMigration(appContext, "${appContext.packageName}_preferences"),
        SharedPreferencesMigration(appContext, appContext.packageName),
    )
}

internal actual fun <T> runBlockingIo(block: suspend CoroutineScope.() -> T): T =
    runBlocking(Dispatchers.IO, block)

private val initLock = Any()

internal actual fun <T> withInitLock(block: () -> T): T = synchronized(initLock) { block() }
