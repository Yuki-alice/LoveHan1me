package io.github.daisukikaffuchino.han1meviewer.logic.dao

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import io.github.daisukikaffuchino.han1meviewer.logic.entity.CheckInRecordEntity

/**
 * 打卡记录库（P2b：下沉到 KMP 共享层，schema / 版本号 / 迁移语义保持不变）。
 */
@Database(
    entities = [CheckInRecordEntity::class],
    version = 5,
    exportSchema = false
)
@ConstructedBy(CheckInRecordDatabaseConstructor::class)
abstract class CheckInRecordDatabase : RoomDatabase() {
    abstract fun checkInDao(): CheckInRecordDao

    // 各端在入口用「对 Companion 的扩展属性」注入懒加载单例（见 :app 的 instance 扩展）
    companion object
}

/** 建库入口（平台 API，见各端 actual）。 */
expect fun createCheckInRecordDatabase(filePath: String): CheckInRecordDatabase

// Room 编译器为每个 target 自动生成 actual object，这里只需声明 expect。
expect object CheckInRecordDatabaseConstructor : RoomDatabaseConstructor<CheckInRecordDatabase> {
    override fun initialize(): CheckInRecordDatabase
}

// ---- 历史迁移：原实现基于 SupportSQLiteDatabase（Android 专属），此处逐条改写为 KMP 的 SQLiteConnection ----

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE check_in_records_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                date TEXT NOT NULL,
                type TEXT NOT NULL DEFAULT '自慰',
                feeling TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        // 原实现在遍历 Cursor 的同时写入；KMP 下先把旧数据读完再写，结果一致且避免读写游标互相干扰
        val legacy = mutableListOf<Pair<String, Int>>()
        val read = connection.prepare("SELECT date, count FROM check_in_records")
        try {
            while (read.step()) legacy += read.getText(0) to read.getInt(1)
        } finally {
            read.close()
        }
        val insert = connection.prepare(
            "INSERT INTO check_in_records_new (date, type, feeling) VALUES (?, '自慰', '')"
        )
        try {
            legacy.forEach { (date, count) ->
                repeat(count.coerceAtMost(20)) {
                    insert.bindText(1, date)
                    insert.step()
                    insert.reset()
                    insert.clearBindings()
                }
            }
        } finally {
            insert.close()
        }
        connection.execSQL("DROP TABLE check_in_records")
        connection.execSQL("ALTER TABLE check_in_records_new RENAME TO check_in_records")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE check_in_records ADD COLUMN time TEXT NOT NULL DEFAULT ''"
        )
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) = Unit
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE check_in_records_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                date TEXT NOT NULL,
                time TEXT NOT NULL,
                type TEXT NOT NULL,
                feeling TEXT NOT NULL
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            INSERT INTO check_in_records_new (id, date, time, type, feeling)
            SELECT id, date, time, type, feeling FROM check_in_records
            """.trimIndent()
        )
        connection.execSQL("DROP TABLE check_in_records")
        connection.execSQL("ALTER TABLE check_in_records_new RENAME TO check_in_records")
        connection.execSQL("DROP TABLE IF EXISTS sidedishes")
    }
}

/** 供各端 actual 注册（顶层属性按声明顺序初始化，故放在各 Migration 之后）。 */
internal val CHECK_IN_RECORD_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
)
