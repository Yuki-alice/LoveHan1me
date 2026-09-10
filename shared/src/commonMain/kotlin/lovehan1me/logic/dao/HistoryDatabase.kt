package lovehan1me.logic.dao

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import lovehan1me.logic.entity.HanimeAdvancedSearchHistoryEntity
import lovehan1me.logic.entity.SearchHistoryEntity
import lovehan1me.logic.entity.WatchHistoryEntity

/**
 * @project Hanime1
 * @author Yenaly Liew
 * @time 2022/06/22 022 22:46
 *
 * P2b：下沉到 KMP 共享层，schema / 版本号 / 迁移语义保持不变。
 */
@Database(
    entities = [SearchHistoryEntity::class,
        WatchHistoryEntity::class,
        HanimeAdvancedSearchHistoryEntity::class],
    version = 4, exportSchema = false
)
@ConstructedBy(HistoryDatabaseConstructor::class)
abstract class HistoryDatabase : RoomDatabase() {

    abstract val searchHistory: SearchHistoryDao

    abstract val watchHistory: WatchHistoryDao

    abstract val hanimeAdvancedSearchHistory: HanimeAdvancedSearchHistoryDao

    // 各端在入口用「对 Companion 的扩展属性」注入懒加载单例（见 :app 的 instance 扩展）
    companion object

    // ---- 历史迁移：原实现基于 SupportSQLiteDatabase（Android 专属），逐条改写为 KMP 的 SQLiteConnection ----

    object Migration1To2 : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            // 原实现在遍历 Cursor 的同时 update；KMP 下先把旧数据读完再写，结果一致且避免读写游标互相干扰
            val legacy = mutableListOf<Pair<Int, String>>()
            val read = connection.prepare("SELECT id, redirectLink FROM WatchHistoryEntity")
            try {
                while (read.step()) legacy += read.getInt(0) to read.getText(1)
            } finally {
                read.close()
            }
            // 原实现用 contentValuesOf + db.update(CONFLICT_REPLACE)，等价于 UPDATE OR REPLACE
            val update = connection.prepare(
                "UPDATE OR REPLACE WatchHistoryEntity SET redirectLink = ? WHERE id = ?"
            )
            try {
                legacy.forEach { (id, url) ->
                    // 不用 String.toVideoCode() 的原因是，防止該拓展函數因不可抗力改變導致 migrate 失敗
                    val videoCode = url.substringAfter("v=")
                    update.bindText(1, videoCode)
                    update.bindInt(2, id)
                    update.step()
                    update.reset()
                    update.clearBindings()
                }
            } finally {
                update.close()
            }
            connection.execSQL(
                """ALTER TABLE WatchHistoryEntity
                   RENAME COLUMN redirectLink TO videoCode"""
            )
        }
    }

    object Migration2To3 : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            // 增加播放进度列，默认值为 0
            connection.execSQL(
                """ALTER TABLE WatchHistoryEntity
                   ADD COLUMN progress INTEGER NOT NULL DEFAULT 0"""
            )
        }
    }

    object Migration3To4 : Migration(3, 4) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `HanimeAdvancedSearchHistory` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `query` TEXT,
                    `genre` TEXT,
                    `sort` TEXT,
                    `broad` INTEGER,
                    `date` TEXT,
                    `duration` TEXT,
                    `tags` TEXT,
                    `brands` TEXT,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }
}

/** 建库入口（平台 API，见各端 actual）。 */
expect fun createHistoryDatabase(filePath: String): HistoryDatabase

// Room 编译器为每个 target 自动生成 actual object，这里只需声明 expect。
expect object HistoryDatabaseConstructor : RoomDatabaseConstructor<HistoryDatabase> {
    override fun initialize(): HistoryDatabase
}

/** 供各端 actual 注册。 */
internal val HISTORY_MIGRATIONS: Array<Migration> = arrayOf(
    HistoryDatabase.Migration1To2,
    HistoryDatabase.Migration2To3,
    HistoryDatabase.Migration3To4,
)
