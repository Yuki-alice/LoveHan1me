package io.github.daisukikaffuchino.han1meviewer.ui.model

import io.github.daisukikaffuchino.han1meviewer.logic.model.SearchOption
import org.jetbrains.compose.resources.StringResource

// P6d-3-C4：titleRes: Int → StringResource（P6d-1-B1 先例）。
data class SearchScopeSection(
    val titleRes: StringResource,
    val options: List<SearchOption>,
    val spanCount: Int = 3,
)
