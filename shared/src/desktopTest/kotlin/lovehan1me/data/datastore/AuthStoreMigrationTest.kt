package lovehan1me.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// P0-1 守卫：登录态独立文件。真 DataStore（临时目录）实测迁移与合并，
// 不是 mock —— 迁移写错会把登录态写丢，必须走真文件断言。
class AuthStoreMigrationTest {

    private val tempRoots = mutableListOf<File>()

    @AfterTest
    fun tearDown() {
        tempRoots.forEach { it.deleteRecursively() }
        tempRoots.clear()
    }

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "han1me-auth-${System.nanoTime()}")
            .also { it.mkdirs(); tempRoots += it }

    private fun store(dir: File, name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath { File(dir, name).absolutePath.toPath() }

    @Test
    fun `主存残留键搬到独立存_主存清干净`() = runBlocking {
        val dir = tempDir()
        val main = store(dir, "settings.preferences_pb")
        val auth = store(dir, "auth.preferences_pb")
        main.edit {
            it[booleanPreferencesKey("already_login")] = true
            it[stringPreferencesKey("saved_user_id")] = "u1"
            it[stringPreferencesKey("cookie")] = "sess=abc"
            it[stringPreferencesKey("theme_mode")] = "dark"
        }

        DataStoreManager.migrateAuthKeysToDedicatedStore(main, auth)

        val authFirst = auth.data.first()
        assertEquals(true, authFirst[booleanPreferencesKey("already_login")])
        assertEquals("u1", authFirst[stringPreferencesKey("saved_user_id")])
        assertEquals("sess=abc", authFirst[stringPreferencesKey("cookie")])
        val mainFirst = main.data.first()
        assertFalse(mainFirst.contains(booleanPreferencesKey("already_login")))
        assertFalse(mainFirst.contains(stringPreferencesKey("cookie")))
        // 非登录态键原样保留。
        assertEquals("dark", mainFirst[stringPreferencesKey("theme_mode")])
    }

    @Test
    fun `冲突以独立存为准_幂等`() = runBlocking {
        val dir = tempDir()
        val main = store(dir, "settings.preferences_pb")
        val auth = store(dir, "auth.preferences_pb")
        auth.edit { it[stringPreferencesKey("cookie")] = "new" }
        main.edit { it[stringPreferencesKey("cookie")] = "stale" }

        DataStoreManager.migrateAuthKeysToDedicatedStore(main, auth)

        // auth 侧已有不覆盖（崩溃在写后删前会留双份，以 auth 为准）。
        assertEquals("new", auth.data.first()[stringPreferencesKey("cookie")])
        // 主存残留照删。
        assertFalse(main.data.first().contains(stringPreferencesKey("cookie")))
        // 第二次是 no-op（主存无残留）。
        DataStoreManager.migrateAuthKeysToDedicatedStore(main, auth)
        assertEquals("new", auth.data.first()[stringPreferencesKey("cookie")])
    }

    @Test
    fun `合并视图auth缺键回落主存`() {
        val main = androidx.datastore.preferences.core.mutablePreferencesOf(
            booleanPreferencesKey("already_login") to true,
            stringPreferencesKey("theme_mode") to "dark",
        )
        val auth = androidx.datastore.preferences.core.mutablePreferencesOf(
            stringPreferencesKey("cookie") to "sess=abc",
        )
        val merged = DataStoreManager.mergeAuthPreferences(main, auth)
        // auth 有的覆盖，没有的（already_login）回落主存老值，不闪断。
        assertEquals(true, merged[booleanPreferencesKey("already_login")])
        assertEquals("sess=abc", merged[stringPreferencesKey("cookie")])
        assertEquals("dark", merged[stringPreferencesKey("theme_mode")])
    }

    @Test
    fun `空主存迁移是noop`() = runBlocking {
        val dir = tempDir()
        val main = store(dir, "settings.preferences_pb")
        val auth = store(dir, "auth.preferences_pb")
        // 全新安装：两边都没有登录态键，不抛错不写盘。
        DataStoreManager.migrateAuthKeysToDedicatedStore(main, auth)
        assertTrue(auth.data.first().asMap().isEmpty())
    }
}
