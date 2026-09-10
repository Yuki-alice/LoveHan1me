package lovehan1me.data.database.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

// Desktop(JVM)：文件路径 + BundledSQLiteDriver
actual fun createCheckInRecordDatabase(filePath: String): CheckInRecordDatabase =
    Room.databaseBuilder<CheckInRecordDatabase>(
        name = filePath,
    ) {
        CheckInRecordDatabaseConstructor.initialize()
    }
        .addMigrations(*CHECK_IN_RECORD_MIGRATIONS)
        .fallbackToDestructiveMigration(true)
        .setDriver(BundledSQLiteDriver())
        .build()
