package lovehan1me.data.database.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

// iosMain：覆盖 iosArm64 + iosSimulatorArm64
actual fun createDanmakuDatabase(filePath: String): DanmakuDatabase =
    Room.databaseBuilder<DanmakuDatabase>(
        name = filePath,
    ) {
        DanmakuDatabaseConstructor.initialize()
    }
        .setDriver(BundledSQLiteDriver())
        .build()
