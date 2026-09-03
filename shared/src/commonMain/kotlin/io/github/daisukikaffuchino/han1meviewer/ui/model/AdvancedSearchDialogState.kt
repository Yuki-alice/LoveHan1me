package io.github.daisukikaffuchino.han1meviewer.ui.model

import io.github.daisukikaffuchino.han1meviewer.logic.model.SearchOption

sealed interface AdvancedSearchDialogState {
    val key: String
    val titleRes: Int

    data class SingleChoice(
        override val key: String,
        override val titleRes: Int,
        val options: List<SearchOption>,
        val selectedIndex: Int,
        val onSelect: (SearchOption) -> Unit,
        val onReset: () -> Unit,
    ) : AdvancedSearchDialogState

    data class MultiChoice(
        override val key: String,
        override val titleRes: Int,
        val scopes: List<SearchScopeSection>,
        val selected: Set<SearchOption>,
        val broad: Boolean,
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
        // P6c：原默认 R.string.release_date 在 commonMain 不可用，改由调用方显式传入
        override val titleRes: Int = 0
    }
}