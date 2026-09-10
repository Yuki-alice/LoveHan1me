package lovehan1me.data.database.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

// iosMain：覆盖 iosArm64 + iosSimulatorArm64
actual fun createMiscellanyDatabase(filePath: String): MiscellanyDatabase =
    Room.databaseBuilder<MiscellanyDatabase>(
        name = filePath,
    ) {
        MiscellanyDatabaseConstructor.initialize()
    }
        .setDriver(BundledSQLiteDriver())
        .build()
