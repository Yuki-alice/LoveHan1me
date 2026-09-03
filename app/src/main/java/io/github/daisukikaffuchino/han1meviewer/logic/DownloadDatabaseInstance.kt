package io.github.daisukikaffuchino.han1meviewer.logic

import io.github.daisukikaffuchino.han1meviewer.logic.dao.Han1meDatabases
import io.github.daisukikaffuchino.han1meviewer.logic.dao.DownloadDatabase

/**
 * :app（Android 端）API 兼容转发：实例统一由共享层 [Han1meDatabases] 提供，
 * 避免双入口各自建库（见 Han1meDatabases.android.kt，路径与此前完全一致）。
 */
val DownloadDatabase.Companion.instance: DownloadDatabase
    get() = Han1meDatabases.download
