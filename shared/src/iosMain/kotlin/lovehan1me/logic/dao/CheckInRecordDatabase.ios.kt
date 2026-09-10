package lovehan1me.logic.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

// iosMain：覆盖 iosArm64 + iosSimulatorArm64
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
