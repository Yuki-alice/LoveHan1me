package me.lovehan1me.logic.dao

/**
 * 五库统一入口（P4b：DatabaseRepo/ViewModel 下沉 commonMain 的前置）。
 *
 * 各平台 actual 用 `by lazy { createXxxDatabase(<平台路径>) }` 提供懒加载单例：
 *  - androidMain：路径与 :app 原 `XxxDatabaseInstance.kt` 完全一致（getDatabasePath(<文件名>)），
 *    Context 由 [Han1meDatabaseContext] 提供（HanimeApplication.onCreate → DataStoreManager.initialize(context) 已注入）
 *  - desktopMain：`~/.han1meviewer/db/<文件名>`（桌面新部署，无历史数据）
 *  - iosMain：`Documents/db/<文件名>`（参照 DataStore 的 Documents 姿势）
 *
 * :app 原有的 `XxxDatabase.Companion.instance` 扩展改为转发本入口，保持 API 兼容。
 * 严禁模块内各自建库（同一文件双 Room 实例会导致两份 schema 与数据漂移）。
 */
expect object Han1meDatabases {
    val miscellany: MiscellanyDatabase
    val history: HistoryDatabase
    val download: DownloadDatabase
    val checkInRecord: CheckInRecordDatabase
    val localList: LocalListDatabase
}
