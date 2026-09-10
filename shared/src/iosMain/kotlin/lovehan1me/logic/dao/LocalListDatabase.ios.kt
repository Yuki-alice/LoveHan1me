package lovehan1me.logic.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

// iosMain：覆盖 iosArm64 + iosSimulatorArm64
actual fun createLocalListDatabase(filePath: String): LocalListDatabase =
    Room.databaseBuilder<LocalListDatabase>(
        name = filePath,
    ) {
        LocalListDatabaseConstructor.initialize()
    }
        .setDriver(BundledSQLiteDriver())
        .build()
