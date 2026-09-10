package lovehan1me.logic.dao

import androidx.room.Room

// Android：走系统 SQLite（Context overload，无 setDriver）
actual fun createDownloadDatabase(filePath: String): DownloadDatabase =
    Room.databaseBuilder<DownloadDatabase>(
        context = Han1meDatabaseContext.appContext,
        name = filePath,
    )
        .addMigrations(*DOWNLOAD_MIGRATIONS)
        .build()
