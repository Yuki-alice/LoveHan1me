package lovehan1me.logic.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

// Desktop(JVM)：文件路径 + BundledSQLiteDriver
actual fun createLocalListDatabase(filePath: String): LocalListDatabase =
    Room.databaseBuilder<LocalListDatabase>(
        name = filePath,
    ) {
        LocalListDatabaseConstructor.initialize()
    }
        .setDriver(BundledSQLiteDriver())
        .build()
