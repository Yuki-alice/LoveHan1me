package lovehan1me.site

import lovehan1me.core.domain.model.AppSettings
import lovehan1me.core.domain.model.AutoMirrorGuard
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B3-5 前置策略的编解码守卫。
 *
 * 背景：`auto_mirror_guard` 经 `DataStoreManager` 以整型 id 持久化（读/写两侧共用
 * [AutoMirrorGuard.fromId] 与 `id`）。曾出现"读侧有、写侧漏"导致重启即丢的问题，
 * 这里把映射本身钉住：全枚举可往返、未知值回默认、默认即严格档。
 */
class AutoMirrorGuardTest {

    @Test
    fun `全枚举 id 可往返`() {
        AutoMirrorGuard.entries.forEach { guard ->
            assertEquals(guard, AutoMirrorGuard.fromId(guard.id), "${guard.name} 应能经 id 往返")
        }
    }

    @Test
    fun `未知 id 回严格档`() {
        assertEquals(AutoMirrorGuard.RequireClearance, AutoMirrorGuard.fromId(-1))
        assertEquals(AutoMirrorGuard.RequireClearance, AutoMirrorGuard.fromId(999))
    }

    @Test
    fun `默认即严格档`() {
        assertEquals(AutoMirrorGuard.RequireClearance, AppSettings().autoMirrorGuard)
        assertEquals(AutoMirrorGuard.RequireClearance, AutoMirrorGuard.fromId(0))
    }
}
