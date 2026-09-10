package lovehan1me.logic.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

// Desktop(JVM)：文件路径 + BundledSQLiteDriver（无 Context 可用）
actual fun createMiscellanyDatabase(filePath: String): MiscellanyDatabase =
    Room.databaseBuilder<MiscellanyDatabase>(
        name = filePath,
    ) {
        MiscellanyDatabaseConstructor.initialize()
    }
        .setDriver(BundledSQLiteDriver())
        .build()
