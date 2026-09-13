package lovehan1me.data.database.dao

import androidx.room.Room

// Android：走系统 SQLite（Context overload，无 setDriver），文件路径由调用方提供
actual fun createMiscellanyDatabase(filePath: String): MiscellanyDatabase =
    Room.databaseBuilder<MiscellanyDatabase>(
        context = Han1meDatabaseContext.appContext,
        name = filePath,
    ).build()
