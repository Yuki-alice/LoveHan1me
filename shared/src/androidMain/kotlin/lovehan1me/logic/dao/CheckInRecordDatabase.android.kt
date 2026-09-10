package lovehan1me.logic.dao

import androidx.room.Room

// Android：走系统 SQLite（Context overload，无 setDriver）
actual fun createCheckInRecordDatabase(filePath: String): CheckInRecordDatabase =
    Room.databaseBuilder<CheckInRecordDatabase>(
        context = Han1meDatabaseContext.appContext,
        name = filePath,
    )
        .addMigrations(*CHECK_IN_RECORD_MIGRATIONS)
        .fallbackToDestructiveMigration(true)
        .build()
