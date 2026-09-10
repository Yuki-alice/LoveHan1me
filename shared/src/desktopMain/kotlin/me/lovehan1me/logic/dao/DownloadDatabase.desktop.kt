package me.lovehan1me.logic.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

// Desktop(JVM)：文件路径 + BundledSQLiteDriver
actual fun createDownloadDatabase(filePath: String): DownloadDatabase =
    Room.databaseBuilder<DownloadDatabase>(
        name = filePath,
    ) {
        DownloadDatabaseConstructor.initialize()
    }
        .addMigrations(*DOWNLOAD_MIGRATIONS)
        .setDriver(BundledSQLiteDriver())
        .build()
