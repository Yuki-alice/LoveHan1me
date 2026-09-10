package lovehan1me.data.datastore

/**
 * DataStore 文件的绝对路径（P2b）。
 *
 * Android 端必须与旧实现 `Context.preferencesDataStoreFile(name)` 落盘位置**完全一致**
 * （filesDir/datastore/<fileName>），否则老用户设置会丢。
 * desktop / iOS 端各自选用系统推荐的用户数据目录。
 *
 * @param fileName 含扩展名的文件名，例如 `settings.preferences_pb`
 */
expect fun dataStoreFilePath(fileName: String): String
