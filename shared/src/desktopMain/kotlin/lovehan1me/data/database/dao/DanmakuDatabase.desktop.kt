package lovehan1me.data.database.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

// Desktop(JVM)：文件路径 + BundledSQLiteDriver
actual fun createDanmakuDatabase(filePath: String): DanmakuDatabase =
    Room.databaseBuilder<DanmakuDatabase>(
        name = filePath,
    ) {
        DanmakuDatabaseConstructor.initialize()
    }
        .setDriver(BundledSQLiteDriver())
        .build()
