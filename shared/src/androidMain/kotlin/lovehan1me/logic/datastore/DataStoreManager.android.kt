package lovehan1me.logic.datastore

import android.content.Context
import lovehan1me.logic.dao.Han1meDatabaseContext

/**
 * Android 端初始化入口：先把 applicationContext 注入共享层的 holder
 * （[dataStoreFilePath] 与 [platformPreferenceMigrations] 都要用它），再走通用初始化。
 *
 * 与旧的 `DataStoreManager.initialize(context)` 成员函数签名保持一致，调用点只需 import 本扩展。
 */
fun DataStoreManager.initialize(context: Context) {
    Han1meDatabaseContext.setContext(context.applicationContext)
    initialize()
}
