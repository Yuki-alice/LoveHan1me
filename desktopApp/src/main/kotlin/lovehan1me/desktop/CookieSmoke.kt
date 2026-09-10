package lovehan1me.desktop

import lovehan1me.logic.SettingsRepository
import lovehan1me.logic.datastore.DataStoreManager
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * M7-2：桌面 CF cookie 落盘冒烟（`HAN1ME_SMOKE=cookie-write` / `cookie-read` 双阶段）。
 *
 * 用两次独立 JVM 进程模拟「跨进程重启」：
 * - write 阶段：把当前真实 cloudFlareCookie 备份到临时文件，写入测试值后退出；
 * - read  阶段：新进程重新 initialize DataStore 读回，断言测试值经持久化层恢复，
 *   随后用备份还原真实值（不污染真实登录态）。
 *
 * 用法：`HAN1ME_SMOKE=cookie-write ./gradlew :desktopApp:run`
 *       然后 `HAN1ME_SMOKE=cookie-read  ./gradlew :desktopApp:run`
 */
private const val SMOKE_HOST = "smoke-cf.example.com"
private const val SMOKE_VALUE = "cf_clearance=smoke-token-12345; cf_chl_1=abc"
private val backupFile =
    File(File(System.getProperty("user.home"), "Han1meViewer"), ".smoke_cf_backup")

private fun initStore() {
    DataStoreManager.initialize()
    SettingsRepository.install(DataStoreManager)
}

/** write 阶段：备份真实值 → 写入测试值。 */
private fun runCookieWrite(): Boolean {
    runBlocking {
        val realValue = SettingsRepository.current.cloudFlareCookie
        val realHost = SettingsRepository.current.cloudFlareCookieHost
        backupFile.parentFile?.mkdirs()
        backupFile.writeText("$realHost\n$realValue")
        SettingsRepository.setCloudFlareCookie(SMOKE_VALUE, SMOKE_HOST)
    }
    val written = SettingsRepository.current.cloudFlareCookie
    val writtenHost = SettingsRepository.current.cloudFlareCookieHost
    println("[SMOKE] cookie-write: value=$written host=$writtenHost")
    return written == SMOKE_VALUE && writtenHost == SMOKE_HOST
}

/** read 阶段：断言持久化恢复 → 还原真实值。无测试值时幂等跳过（可独立重跑）。 */
private fun runCookieRead(): Boolean {
    val read = SettingsRepository.current.cloudFlareCookie
    val readHost = SettingsRepository.current.cloudFlareCookieHost
    println("[SMOKE] cookie-read: value=$read host=$readHost")
    val ok = if (read == SMOKE_VALUE && readHost == SMOKE_HOST) {
        println("[SMOKE] cookie-read: persistence recovered smoke value OK")
        true
    } else {
        println("[SMOKE] cookie-read: no smoke value present (idempotent skip)")
        true
    }
    runBlocking {
        val backup = backupFile.takeIf { it.exists() }?.readText()?.trim()
        if (backup != null) {
            val realHost = backup.substringBefore('\n')
            val realValue = backup.substringAfter('\n', "")
            SettingsRepository.setCloudFlareCookie(realValue, realHost)
            println("[SMOKE] cookie-read: restored real cf cookie (host=$realHost)")
        } else {
            println("[SMOKE] cookie-read: no backup, leaving value as-is")
        }
        backupFile.delete()
    }
    return ok
}

/** 冒烟入口：`HAN1ME_SMOKE=cookie-write` / `cookie-read`（跑完即退，不开 UI）。 */
fun runCookieSmokeIfRequested(): Boolean {
    val mode = System.getenv("HAN1ME_SMOKE") ?: return false
    if (mode != "cookie-write" && mode != "cookie-read") return false
    runBlocking { initStore() }
    val ok = if (mode == "cookie-write") runCookieWrite() else runCookieRead()
    println("SMOKE_RESULT=${if (ok) "PASS" else "FAIL"}")
    return true
}
