package lovehan1me.data.database.dao

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import lovehan1me.data.database.entity.DanmakuCacheEntity
import lovehan1me.data.database.entity.DanmakuMappingEntity

/**
 * 弹幕库（关联记录 + 弹幕镜像缓存）。
 *
 * 刻意**新建第 5 库**而不是给 [LocalListDatabase] 加表：后者是 version 1 且零
 * migration，加表就得升版，而升版要么写 migration、要么
 * `fallbackToDestructiveMigration` —— 后者会静默清空用户自建歌单，代价不可接受。
 * 独立成库还让弹幕缓存可以整体丢弃而不碰任何其它数据。
 */
@Database(
    entities = [DanmakuMappingEntity::class, DanmakuCacheEntity::class],
    version = 1,
    exportSchema = false,
)
@ConstructedBy(DanmakuDatabaseConstructor::class)
abstract class DanmakuDatabase : RoomDatabase() {

    abstract val danmakuDao: DanmakuDao

    // 各端在入口用「对 Companion 的扩展属性」注入懒加载单例（见 Han1meDatabases）
    companion object
}

/** 建库入口（平台 API，见各端 actual）。 */
expect fun createDanmakuDatabase(filePath: String): DanmakuDatabase

// Room 编译器为每个 target 自动生成 actual object，这里只需声明 expect。
expect object DanmakuDatabaseConstructor : RoomDatabaseConstructor<DanmakuDatabase> {
    override fun initialize(): DanmakuDatabase
}
