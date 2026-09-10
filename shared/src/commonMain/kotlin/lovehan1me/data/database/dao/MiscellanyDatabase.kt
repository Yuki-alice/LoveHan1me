package lovehan1me.data.database.dao

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import lovehan1me.data.database.entity.HKeyframeEntity

/**
 * 各种有数据库需求的小功能的聚集地（P2 spike：首个下沉到 KMP 共享层的数据库）。
 *
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2023/11/12 012 12:28
 */
@Database(
    entities = [HKeyframeEntity::class],
    version = 1, exportSchema = false
)
@ConstructedBy(MiscellanyDatabaseConstructor::class)
abstract class MiscellanyDatabase : RoomDatabase() {

    abstract val hKeyframeDao: HKeyframeDao

    // 各端在 :app / 平台入口用「对 Companion 的扩展属性」注入懒加载单例（见 :app 的 instance 扩展）
    companion object
}

/**
 * 建库入口（平台 API，见各端 actual）：
 * `Room.databaseBuilder` 只在 room-runtime 的平台源集里声明，commonMain 元数据看不到它，
 * 因此必须由各端提供 actual——统一用 BundledSQLiteDriver + 文件路径，不依赖 Android Context。
 * jvmMain（android+desktop）与 iosMain 各有一份 actual。
 */
expect fun createMiscellanyDatabase(filePath: String): MiscellanyDatabase

// Room 编译器为每个 target 自动生成 actual object，这里只需声明 expect。
expect object MiscellanyDatabaseConstructor : RoomDatabaseConstructor<MiscellanyDatabase> {
    override fun initialize(): MiscellanyDatabase
}
