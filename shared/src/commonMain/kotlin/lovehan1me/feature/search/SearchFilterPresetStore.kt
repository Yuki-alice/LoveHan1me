package lovehan1me.feature.search

import lovehan1me.core.domain.model.SearchFilterPreset
import lovehan1me.core.domain.model.SearchFilterSnapshot
import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.data.SettingsRepository

/**
 * 命名筛选预设的增删改。
 *
 * 与「高级搜索历史」（`HanimeAdvancedSearchRepo`，Room，自动记录、最多 10 条、同条件去重）的分工：
 * 这里是**用户主动**保存并命名的常用组合，存在 DataStore 上（因此自动进备份）。
 *
 * 结构：suspend 入口只做「读整表 → 纯函数算出新表 → 写回」，
 * 真正的列表语义（同名覆盖、保序、上限）全在 [upsert] / [removeById] 这两个纯函数里，
 * 于是它们可以脱离 `SettingsRepository.install()` 直接单测。
 */
object SearchFilterPresetStore {

    /**
     * 预设条数上限。
     *
     * 预设是给用户**快速点选**的（常驻栏最宽也就 240dp），堆太多反而找不到；
     * 超限时丢最早插入的那条。
     */
    const val MAX_PRESETS = 20

    /** 当前全部预设（顺序 = 插入顺序，最早的在前）。 */
    fun presets(): List<SearchFilterPreset> = SettingsRepository.searchFilterPresets

    /**
     * 保存当前筛选为预设。
     *
     * **同名即覆盖**：用户用同一个名字再存一次，意图是"更新那条"，所以保留原 id 与原位置
     * （否则每改一次条件，芯片就会跳到列表末尾，用户会觉得它消失了）。
     *
     * @return 写入的预设；[name] 全为空白时返回 null（不写盘）。
     */
    suspend fun save(name: String, snapshot: SearchFilterSnapshot): SearchFilterPreset? {
        val normalized = normalizeName(name) ?: return null
        val preset = SearchFilterPreset(
            id = newId(normalized),
            name = normalized,
            createdAt = currentEpochMillis(),
            snapshot = snapshot,
        )
        SettingsRepository.setSearchFilterPresets(upsert(presets(), preset))
        return preset
    }

    /** 按 id 删除；id 不存在时是 no-op（不写盘）。 */
    suspend fun remove(id: String) {
        val current = presets()
        val next = removeById(current, id)
        if (next.size != current.size) SettingsRepository.setSearchFilterPresets(next)
    }

    // ------------------------------------------------------------------ 纯函数区

    /**
     * trim + 折叠内部连续空白为单个空格；全空白返回 null。
     *
     * 折叠是为了让"夏日  合集"和"夏日 合集"被判成同一个名字（否则会出现两个看起来一样的芯片）。
     */
    internal fun normalizeName(raw: String): String? =
        raw.trim().replace(WHITESPACE_RUN, " ").ifBlank { null }

    /**
     * 插入或按**名字**覆盖，保持插入顺序，超出 [MAX_PRESETS] 时丢掉最早的。
     *
     * 同名覆盖时沿用原有的 [SearchFilterPreset.id] 与 `createdAt`：id 稳定意味着
     * UI 侧以 id 为 key 的重组不会把芯片当成"删了又加"，位置也不会跳。
     */
    internal fun upsert(
        existing: List<SearchFilterPreset>,
        incoming: SearchFilterPreset,
    ): List<SearchFilterPreset> {
        val index = existing.indexOfFirst { it.name == incoming.name }
        val merged = if (index >= 0) {
            val kept = existing[index]
            existing.toMutableList().also {
                it[index] = incoming.copy(id = kept.id, createdAt = kept.createdAt)
            }
        } else {
            existing + incoming
        }
        // 顺序即插入顺序（最早的在前），所以溢出一律砍最前面那条。
        return if (merged.size <= MAX_PRESETS) merged else merged.takeLast(MAX_PRESETS)
    }

    internal fun removeById(
        existing: List<SearchFilterPreset>,
        id: String,
    ): List<SearchFilterPreset> = existing.filterNot { it.id == id }

    /**
     * id = 毫秒时间戳 + 名字哈希。
     *
     * 只用时间戳的话，同一毫秒内保存两个不同名字的预设会撞 id，
     * 之后按 id 删除会一次删掉两条。
     */
    private fun newId(name: String): String = "${currentEpochMillis()}-${name.hashCode()}"

    private val WHITESPACE_RUN = Regex("\\s+")
}
