package io.github.daisukikaffuchino.han1meviewer.logic.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

// iOS：全新落盘，无历史 SharedPreferences 可迁
internal actual fun platformPreferenceMigrations(): List<DataMigration<Preferences>> = emptyList()

internal actual fun <T> runBlockingIo(block: suspend CoroutineScope.() -> T): T =
    runBlocking(Dispatchers.Default, block)

// Kotlin/Native 没有 synchronized；初始化只发生在 App 启动的主线程，直接执行即可
internal actual fun <T> withInitLock(block: () -> T): T = block()
