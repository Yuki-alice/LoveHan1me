package lovehan1me.data.database.dao

import androidx.room.Room

// Android：走系统 SQLite（Context overload，无 setDriver）
actual fun createLocalListDatabase(filePath: String): LocalListDatabase =
    Room.databaseBuilder<LocalListDatabase>(
        context = Han1meDatabaseContext.appContext,
        name = filePath,
    ).build()
