package lovehan1me.logic.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

// iosMain：覆盖 iosArm64 + iosSimulatorArm64
actual fun createDownloadDatabase(filePath: String): DownloadDatabase =
    Room.databaseBuilder<DownloadDatabase>(
        name = filePath,
    ) {
        DownloadDatabaseConstructor.initialize()
    }
        .addMigrations(*DOWNLOAD_MIGRATIONS)
        .setDriver(BundledSQLiteDriver())
        .build()
