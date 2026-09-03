package io.github.daisukikaffuchino.han1meviewer.logic.dao

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import io.github.daisukikaffuchino.han1meviewer.logic.dao.download.DownloadCategoryDao
import io.github.daisukikaffuchino.han1meviewer.logic.dao.download.HanimeDownloadDao
import io.github.daisukikaffuchino.han1meviewer.logic.entity.download.DownloadCategoryEntity
import io.github.daisukikaffuchino.han1meviewer.logic.entity.download.DownloadGroupEntity
import io.github.daisukikaffuchino.han1meviewer.logic.entity.download.HanimeCategoryCrossRef
import io.github.daisukikaffuchino.han1meviewer.logic.entity.download.HanimeDownloadEntity
import io.github.daisukikaffuchino.han1meviewer.logic.state.DownloadState

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2022/08/07 007 18:26
 *
 * P2b：下沉到 KMP 共享层，schema / 版本号 / 迁移语义保持不变。
 */
@Database(
    entities = [HanimeDownloadEntity::class, DownloadCategoryEntity::class, HanimeCategoryCrossRef::class, DownloadGroupEntity::class],
    version = 5, exportSchema = false
)
@ConstructedBy(DownloadDatabaseConstructor::class)
abstract class DownloadDatabase : RoomDatabase() {

    abstract val hanimeDownloadDao: HanimeDownloadDao
    abstract val downloadCategoryDao: DownloadCategoryDao
    abstract val downloadGroupDao: DownloadGroupDao

    // 各端在入口用「对 Companion 的扩展属性」注入懒加载单例（见 :app 的 instance 扩展）
    companion object

    // ---- 历史迁移：原实现基于 SupportSQLiteDatabase（Android 专属），逐条改写为 KMP 的 SQLiteConnection ----

    object Migration1To2 : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """CREATE TABLE IF NOT EXISTS `HanimeDownloadEntity`(
                    `coverUrl` TEXT NOT NULL, `title` TEXT NOT NULL,
                    `addDate` INTEGER NOT NULL, `videoCode` TEXT NOT NULL,
                    `videoUri` TEXT NOT NULL, `quality` TEXT NOT NULL,
                    `videoUrl` TEXT NOT NULL, `length` INTEGER NOT NULL,
                    `downloadedLength` INTEGER NOT NULL, `isDownloading` INTEGER NOT NULL,
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)""".trimIndent()
            )
            connection.execSQL(
                """INSERT INTO `HanimeDownloadEntity`(
                        `coverUrl`, `title`, `addDate`,
                        `videoCode`, `videoUri`, `quality`,
                        `videoUrl`, `length`, `downloadedLength`, `isDownloading`, `id`)
                     SELECT `coverUrl`, `title`, `addDate`, `videoCode`, `videoUri`, `quality`,
                        '' AS `videoUrl`, 1 AS `length`, 1 AS `downloadedLength`, 0 AS `isDownloading`,
                        `id`
                     FROM `HanimeDownloadedEntity`""".trimIndent()
            )
            connection.execSQL("""DROP TABLE IF EXISTS HanimeDownloadedEntity""")
        }
    }

    object Migration2To3 : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """CREATE TABLE IF NOT EXISTS `DownloadCategoryEntity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)"""
            )
            connection.execSQL(
                """CREATE TABLE IF NOT EXISTS `HanimeCategoryCrossRef` (`videoId` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, PRIMARY KEY(`videoId`, `categoryId`))"""
            )
            connection.execSQL("""CREATE INDEX IF NOT EXISTS `index_HanimeCategoryCrossRef_categoryId` ON `HanimeCategoryCrossRef` (`categoryId`)""")
            // Add coverUri column
            connection.execSQL("""ALTER TABLE `HanimeDownloadEntity` ADD COLUMN `coverUri` TEXT NULL""")

            // Add state column with default value (convert from isDownloading)
            connection.execSQL("""ALTER TABLE `HanimeDownloadEntity` ADD COLUMN `state` INTEGER NOT NULL DEFAULT ${DownloadState.Mask.UNKNOWN}""")

            // Update state values based on isDownloading
            // If isDownloading=1, set state to DOWNLOADING (2)
            // If isDownloading=0,
            //                     if downloadedLength=length, set state to FINISHED (4)
            //                     else set state to PAUSED (3)
            connection.execSQL(
                """UPDATE `HanimeDownloadEntity` SET `state` = 
                    |CASE WHEN `isDownloading` = 1 THEN ${DownloadState.Mask.DOWNLOADING} ELSE 
                    |CASE WHEN `downloadedLength` = `length` THEN ${DownloadState.Mask.FINISHED} 
                    |ELSE ${DownloadState.Mask.PAUSED} END END""".trimMargin()
            )
        }
    }

    object Migration3To4 : Migration(3, 4) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
            CREATE TABLE IF NOT EXISTS `HanimeDownloadEntity_new` (
                `coverUrl` TEXT NOT NULL,
                `coverUri` TEXT,
                `title` TEXT NOT NULL,
                `addDate` INTEGER NOT NULL,
                `videoCode` TEXT NOT NULL,
                `videoUri` TEXT NOT NULL,
                `quality` TEXT NOT NULL,
                `videoUrl` TEXT NOT NULL DEFAULT '',
                `length` INTEGER NOT NULL DEFAULT 1,
                `downloadedLength` INTEGER NOT NULL DEFAULT 0,
                `state` INTEGER NOT NULL DEFAULT ${DownloadState.Mask.UNKNOWN},
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
            )
            """.trimIndent()
            )
            connection.execSQL(
                """
            INSERT INTO `HanimeDownloadEntity_new` (
                coverUrl, coverUri, title, addDate,
                videoCode, videoUri, quality, videoUrl,
                length, downloadedLength, state, id
            )
            SELECT 
                coverUrl, coverUri, title, addDate,
                videoCode, videoUri, quality,
                videoUrl, length, downloadedLength, state, id
            FROM `HanimeDownloadEntity`
            """.trimIndent()
            )
            connection.execSQL("DROP TABLE `HanimeDownloadEntity`")
            connection.execSQL("ALTER TABLE `HanimeDownloadEntity_new` RENAME TO `HanimeDownloadEntity`")
        }
    }

    object Migration4To5 : Migration(4, 5) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `download_groups` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `name` TEXT NOT NULL,
                    `orderIndex` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )

            connection.execSQL(
                """
                INSERT INTO `download_groups` (`id`, `name`, `orderIndex`) 
                VALUES (${DownloadGroupEntity.DEFAULT_GROUP_ID}, '${DownloadGroupEntity.DEFAULT_GROUP_NAME}', 0)
                """.trimIndent()
            )

            connection.execSQL(
                """
                CREATE TABLE `HanimeDownloadEntity_new` (
                    `coverUrl` TEXT NOT NULL,
                    `coverUri` TEXT,
                    `title` TEXT NOT NULL,
                    `addDate` INTEGER NOT NULL,
                    `videoCode` TEXT NOT NULL,
                    `videoUri` TEXT NOT NULL,
                    `quality` TEXT NOT NULL,
                    `videoUrl` TEXT NOT NULL DEFAULT '',
                    `length` INTEGER NOT NULL DEFAULT 1,
                    `downloadedLength` INTEGER NOT NULL DEFAULT 0,
                    `state` INTEGER NOT NULL DEFAULT ${DownloadState.Mask.UNKNOWN},
                    `groupId` INTEGER NOT NULL DEFAULT ${DownloadGroupEntity.DEFAULT_GROUP_ID},
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    FOREIGN KEY(`groupId`) REFERENCES `download_groups`(`id`) ON UPDATE NO ACTION ON DELETE SET DEFAULT
                )
                """.trimIndent()
            )

            connection.execSQL("""CREATE INDEX IF NOT EXISTS `index_HanimeDownloadEntity_groupId` ON `HanimeDownloadEntity_new` (`groupId`)""")

            connection.execSQL(
                """
                INSERT INTO `HanimeDownloadEntity_new` (
                    coverUrl, coverUri, title, addDate,
                    videoCode, videoUri, quality, videoUrl,
                    length, downloadedLength, state, id,
                    groupId
                )
                SELECT 
                    coverUrl, coverUri, title, addDate,
                    videoCode, videoUri, quality,
                    videoUrl, length, downloadedLength, state, id,
                    ${DownloadGroupEntity.DEFAULT_GROUP_ID} AS groupId
                FROM `HanimeDownloadEntity`
                """.trimIndent()
            )

            connection.execSQL("DROP TABLE `HanimeDownloadEntity`")
            connection.execSQL("ALTER TABLE `HanimeDownloadEntity_new` RENAME TO `HanimeDownloadEntity`")
        }
    }
}

/** 建库入口（平台 API，见各端 actual）。 */
expect fun createDownloadDatabase(filePath: String): DownloadDatabase

// Room 编译器为每个 target 自动生成 actual object，这里只需声明 expect。
expect object DownloadDatabaseConstructor : RoomDatabaseConstructor<DownloadDatabase> {
    override fun initialize(): DownloadDatabase
}

/** 供各端 actual 注册。 */
internal val DOWNLOAD_MIGRATIONS: Array<Migration> = arrayOf(
    DownloadDatabase.Migration1To2,
    DownloadDatabase.Migration2To3,
    DownloadDatabase.Migration3To4,
    DownloadDatabase.Migration4To5,
)
