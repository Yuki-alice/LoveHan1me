package io.github.daisukikaffuchino.han1meviewer.logic.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope

/**
 * 平台特有的 DataStore 迁移链。
 * Android 上是历史遗留的 SharedPreferences → DataStore 迁移（需要 Context，故留在 androidMain）；
 * desktop / iOS 是全新落盘，无需迁移。
 */
internal expect fun platformPreferenceMigrations(): List<DataMigration<Preferences>>

/**
 * commonMain 里没有 `runBlocking`，也没有 `Dispatchers.IO`，
 * 初始化时的一次性阻塞读写交由各端提供。
 */
internal expect fun <T> runBlockingIo(block: suspend CoroutineScope.() -> T): T

/** commonMain 里没有 `synchronized`，初始化临界区交由各端提供。 */
internal expect fun <T> withInitLock(block: () -> T): T
