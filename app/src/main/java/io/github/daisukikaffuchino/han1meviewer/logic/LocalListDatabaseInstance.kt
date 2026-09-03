package io.github.daisukikaffuchino.han1meviewer.logic

import io.github.daisukikaffuchino.han1meviewer.logic.dao.Han1meDatabases
import io.github.daisukikaffuchino.han1meviewer.logic.dao.LocalListDatabase

/**
 * :app（Android 端）API 兼容转发：实例统一由共享层 [Han1meDatabases] 提供，
 * 避免双入口各自建库（见 Han1meDatabases.android.kt，路径与此前完全一致）。
 */
val LocalListDatabase.Companion.instance: LocalListDatabase
    get() = Han1meDatabases.localList
