package lovehan1me.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 上游 HanimeViewer 备份包兼容：包名不同导致多态鉴别名不同，
// 直接解码必炸；宽容解码只认 type 后缀简单名 + 值形状。
// fixture 按上游 BackupManager 原样手写（prettyPrint / 上游 FQN / 上游独有键 / hKeyframes）。
class BackupUpstreamCompatTest {

    private val upstreamBackup = """
    {
        "version": 1,
        "appVersionCode": 260805,
        "appVersionName": "26.3.2",
        "exportedAt": 1754000000000,
        "settings": {
            "download_count_limit": {"type": "io.github.daisukikaffuchino.han1meviewer.logic.BackupManager.PreferenceValue.IntValue", "value": 3},
            "update_checked_at_ms": {"type": "io.github.daisukikaffuchino.han1meviewer.logic.BackupManager.PreferenceValue.LongValue", "value": 1754000000000},
            "slide_sensitivity": {"type": "io.github.daisukikaffuchino.han1meviewer.logic.BackupManager.PreferenceValue.FloatValue", "value": 1.5},
            "cookie": {"type": "io.github.daisukikaffuchino.han1meviewer.logic.BackupManager.PreferenceValue.StringValue", "value": "sess=abc"},
            "fake_launcher_icon": {"type": "io.github.daisukikaffuchino.han1meviewer.logic.BackupManager.PreferenceValue.BooleanValue", "value": true},
            "use_ech": {"type": "io.github.daisukikaffuchino.han1meviewer.logic.BackupManager.PreferenceValue.BooleanValue", "value": false},
            "typeless_flag": {"value": true},
            "typeless_name": {"value": "plain"}
        },
        "hKeyframes": [{"id": 1, "videoCode": "v1", "time": 1000}],
        "checkInRecords": [{"id": 0, "date": "2026-10-01", "time": "08:00", "type": "day", "feeling": "good"}],
        "watchHistories": [{"coverUrl": "https://img/a.jpg", "title": "t", "releaseDate": 100, "watchDate": 200, "videoCode": "v9", "progress": 10}],
        "downloadGroups": [{"name": "g", "orderIndex": 0, "id": 1}]
    }
    """.trimIndent()

    @Test
    fun `上游包整体可解_上游独有键被忽略或透传`() {
        val backup = BackupManager.decodeBackupJson(upstreamBackup)
        assertEquals(1, backup.version)
        assertEquals(1, backup.checkInRecords?.size)
        assertEquals("v9", backup.watchHistories?.single()?.videoCode)
        assertEquals(1, backup.downloadGroups?.size)
        // 上游独有表直接忽略，不炸。
        assertNull(backup.localLists)
        assertNull(backup.danmakuMappings)
    }

    @Test
    fun `设置值按声明类型还原_IntLong不互串`() {
        val backup = BackupManager.decodeBackupJson(upstreamBackup)
        val settings = BackupManager.decodeSettingsValues(backup.settings.orEmpty())
        // Int 还是 Int（错成 Long 会让读侧落默认值，比值错更糟）。
        assertIs<Int>(settings["download_count_limit"])
        assertEquals(3, settings["download_count_limit"])
        assertIs<Long>(settings["update_checked_at_ms"])
        assertEquals(1754000000000L, settings["update_checked_at_ms"])
        assertIs<Float>(settings["slide_sensitivity"])
        assertEquals("sess=abc", settings["cookie"])
        assertEquals(true, settings["fake_launcher_icon"])
        assertEquals(false, settings["use_ech"])
        // 无 type 形状兜底。
        assertEquals(true, settings["typeless_flag"])
        assertEquals("plain", settings["typeless_name"])
    }

    @Test
    fun `本机包仍可解_鉴别名后缀同名`() {
        val ours = """
        {
            "version": 1,
            "settings": {
                "download_count_limit": {"type": "lovehan1me.data.BackupManager.PreferenceValue.IntValue", "value": 2},
                "cookie": {"type": "lovehan1me.data.BackupManager.PreferenceValue.StringValue", "value": "sess=xyz"}
            }
        }
        """.trimIndent()
        val settings = BackupManager.decodeSettingsValues(
            BackupManager.decodeBackupJson(ours).settings.orEmpty()
        )
        assertEquals(2, settings["download_count_limit"])
        assertEquals("sess=xyz", settings["cookie"])
    }

    @Test
    fun `坏键只丢该键不连累整包`() {
        val broken = """
        {
            "version": 1,
            "settings": {
                "good": {"type": "x.BooleanValue", "value": true},
                "bad_int": {"type": "x.IntValue", "value": "not-a-number"},
                "no_value": {"type": "x.StringValue"}
            }
        }
        """.trimIndent()
        val settings = BackupManager.decodeSettingsValues(
            BackupManager.decodeBackupJson(broken).settings.orEmpty()
        )
        assertEquals(true, settings["good"])
        assertTrue("bad_int" !in settings)
        assertTrue("no_value" !in settings)
    }
}
