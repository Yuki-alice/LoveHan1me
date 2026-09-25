package lovehan1me.core.util

import lovehan1me.core.domain.model.ReportReason
import lovehan1me.core.domain.model.SearchOption
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [decodeComposeAsset] 的护栏。
 *
 * 读取层一旦退化（composeResources 前缀漂移、或某个 actual 恒返回 null），UI 上的表现是
 *「发现页高级筛选弹窗只剩标题和重置/取消」—— 从界面几乎反推不到是文件没读到。
 * 桌面端 2026-09-16（classpath 前缀写错）、iOS 2026-09-24（actual 占位 `= null`）各踩一次，
 * 所以在这里直接钉住数据源本身，哪一端的 actual 坏了就在哪一端红。
 *
 * Android host 单测特例：assets 管线在 host JVM 上不存在（APK 未打包），7 份全空
 * 说明是管线缺失而非文件缺失，此时 SKIP（真机形态由 instrumental/桌面覆盖；
 * desktopTest 同套用例常跑，CI 不会漏）。部分为空则是真回归，直接 FAIL。
 */
class ComposeAssetsSyncTest {

    private val allPaths = listOf(
        GENRE_JSON,
        SORT_JSON,
        DURATION_JSON,
        RELEASE_DATE_JSON,
        BRANDS_JSON,
        TAGS_JSON,
        REPORT_REASON_JSON,
    )

    // 任一用例先过门：7 份全空 = 本宿主无 assets 管线，SKIP；否则继续断言。
    private fun gateHostPipeline(): Boolean {
        val readable = allPaths.count { decodeComposeAsset<JsonElement>(it) != null }
        if (readable == 0) {
            println("[SKIP] ComposeAssetsSyncTest：本宿主无 assets 管线（Android host 单测形态），转 desktopTest 覆盖")
            return false
        }
        return true
    }

    @Test
    fun `筛选选项数据源都能读到且非空`() {
        if (!gateHostPipeline()) return
        assertTrue(readOptions(GENRE_JSON).isNotEmpty(), "$GENRE_JSON 为空")
        assertTrue(readOptions(SORT_JSON).isNotEmpty(), "$SORT_JSON 为空")
        assertTrue(readOptions(DURATION_JSON).isNotEmpty(), "$DURATION_JSON 为空")
        assertTrue(readOptions(RELEASE_DATE_JSON).isNotEmpty(), "$RELEASE_DATE_JSON 为空")
        assertTrue(readOptions(BRANDS_JSON).isNotEmpty(), "$BRANDS_JSON 为空")
    }

    @Test
    fun `标签数据源能读到且非空`() {
        if (!gateHostPipeline()) return
        val tags = decodeComposeAsset<Map<String, List<SearchOption>>>(TAGS_JSON)
        assertNotNull(tags, "$TAGS_JSON 读取失败：composeResources 前缀可能已漂移")
        assertTrue(tags.isNotEmpty(), "$TAGS_JSON 为空")
    }

    @Test
    fun `举报原因数据源能读到且非空`() {
        if (!gateHostPipeline()) return
        val reasons = decodeComposeAsset<List<ReportReason>>(REPORT_REASON_JSON)
        assertNotNull(reasons, "$REPORT_REASON_JSON 读取失败：composeResources 前缀可能已漂移")
        assertTrue(reasons.isNotEmpty(), "$REPORT_REASON_JSON 为空")
    }

    private fun readOptions(path: String): List<SearchOption> {
        val options = decodeComposeAsset<List<SearchOption>>(path)
        assertNotNull(options, "$path 读取失败：composeResources 前缀可能已漂移")
        return options
    }

    private companion object {
        const val GENRE_JSON = "files/search_options/genre.json"
        const val SORT_JSON = "files/search_options/sort_option.json"
        const val DURATION_JSON = "files/search_options/duration.json"
        const val RELEASE_DATE_JSON = "files/search_options/release_date.json"
        const val BRANDS_JSON = "files/search_options/brands.json"
        const val TAGS_JSON = "files/search_options/tags.json"
        const val REPORT_REASON_JSON = "files/report_reason.json"
    }
}
