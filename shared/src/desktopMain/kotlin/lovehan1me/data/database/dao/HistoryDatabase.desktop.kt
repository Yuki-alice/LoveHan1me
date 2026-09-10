package lovehan1me.data.database.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

// Desktop(JVM)：文件路径 + BundledSQLiteDriver
actual fun createHistoryDatabase(filePath: String): HistoryDatabase =
    Room.databaseBuilder<HistoryDatabase>(
        name = filePath,
    ) {
        HistoryDatabaseConstructor.initialize()
    }
        .addMigrations(*HISTORY_MIGRATIONS)
        .setDriver(BundledSQLiteDriver())
        .build()
