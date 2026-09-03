package io.github.daisukikaffuchino.han1meviewer.logic.dao

import androidx.room.Room

// Android：走系统 SQLite（Context overload，无 setDriver）
actual fun createHistoryDatabase(filePath: String): HistoryDatabase =
    Room.databaseBuilder<HistoryDatabase>(
        context = Han1meDatabaseContext.appContext,
        name = filePath,
    )
        .addMigrations(*HISTORY_MIGRATIONS)
        .build()
