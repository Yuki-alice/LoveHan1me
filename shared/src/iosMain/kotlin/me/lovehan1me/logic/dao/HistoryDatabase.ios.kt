package me.lovehan1me.logic.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

// iosMain：覆盖 iosArm64 + iosSimulatorArm64
actual fun createHistoryDatabase(filePath: String): HistoryDatabase =
    Room.databaseBuilder<HistoryDatabase>(
        name = filePath,
    ) {
        HistoryDatabaseConstructor.initialize()
    }
        .addMigrations(*HISTORY_MIGRATIONS)
        .setDriver(BundledSQLiteDriver())
        .build()
