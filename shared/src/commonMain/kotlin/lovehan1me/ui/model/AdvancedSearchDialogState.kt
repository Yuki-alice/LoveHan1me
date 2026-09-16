package lovehan1me.ui.model

import lovehan1me.Res
import lovehan1me.core.domain.model.SearchOption
import lovehan1me.release_date
import org.jetbrains.compose.resources.StringResource

// P6d-3-C4：titleRes: Int → StringResource（P6d-1-B1 先例）。
sealed interface AdvancedSearchDialogState {
    val key: String
    val titleRes: StringResource

    data class SingleChoice(
        override val key: String,
        override val titleRes: StringResource,
        val options: List<SearchOption>,
        val selectedIndex: Int,
        val onSelect: (SearchOption) -> Unit,
        val onReset: () -> Unit,
    ) : AdvancedSearchDialogState

    /**
     * @param showBroad 是否显示「宽泛配对」开关。品牌等多选域在站点侧不参与
     *   `broad=on`（只对 `tags[]` 生效），显示开关会让用户以为品牌也受它影响。
     */
    data class MultiChoice(
        override val key: String,
        override val titleRes: StringResource,
        val scopes: List<SearchScopeSection>,
        val selected: Set<SearchOption>,
        val broad: Boolean,
        val showBroad: Boolean = true,
        val onSave: (Set<SearchOption>, Boolean) -> Unit,
        val onReset: () -> Unit,
    ) : AdvancedSearchDialogState

    data class ReleaseDate(
        override val key: String,
        val options: List<SearchOption>,
        val initialApproximate: String?,
        val initialYear: Int?,
        val initialMonth: Int?,
        val onSaveApproximate: (String?) -> Unit,
        val onSaveSpecific: (Int, Int?) -> Unit,
        val onReset: () -> Unit,
    ) : AdvancedSearchDialogState {
        // P6c 时 R.string.release_date 在 commonMain 不可用而置 0（实际未被读取，
        // 对话框标题硬编码同键）；现资源就位，改回真实默认值
        override val titleRes: StringResource = Res.string.release_date
    }
}
