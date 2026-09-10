package lovehan1me.data.database.dao

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import lovehan1me.data.database.entity.LocalListEntity
import lovehan1me.data.database.entity.LocalListItemEntity

/**
 * 本地清单库（P2b：下沉到 KMP 共享层，schema 与版本号保持不变）。
 */
@Database(
    entities = [LocalListEntity::class, LocalListItemEntity::class],
    version = 1,
    exportSchema = false,
)
@ConstructedBy(LocalListDatabaseConstructor::class)
abstract class LocalListDatabase : RoomDatabase() {

    abstract val localListDao: LocalListDao

    // 各端在入口用「对 Companion 的扩展属性」注入懒加载单例（见 :app 的 instance 扩展）
    companion object
}

/** 建库入口（平台 API，见各端 actual）。 */
expect fun createLocalListDatabase(filePath: String): LocalListDatabase

// Room 编译器为每个 target 自动生成 actual object，这里只需声明 expect。
expect object LocalListDatabaseConstructor : RoomDatabaseConstructor<LocalListDatabase> {
    override fun initialize(): LocalListDatabase
}
