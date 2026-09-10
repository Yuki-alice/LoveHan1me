package lovehan1me.app.navigation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import lovehan1me.feature.search.AdvancedSearchSheet
import lovehan1me.feature.search.SearchScreen
import lovehan1me.feature.search.SearchViewModel
import lovehan1me.app.sharedViewModel
import lovehan1me.core.util.rememberCopyTextToClipboard
import kotlinx.serialization.json.Json

@Composable
fun SearchRouteScreen(
    route: SearchRoute,
    onBack: () -> Unit,
    onNavigateToVideo: (String) -> Unit,
) {
    val viewModel: SearchViewModel = sharedViewModel(::SearchViewModel)
    var showAdvancedSearchSheet by remember { mutableStateOf(false) }

    LaunchedEffect(route.advancedSearchJson) {
        route.advancedSearchJson?.let { json ->
            runCatching { Json.decodeFromString<Map<String, String>>(json) }
                .onSuccess { params ->
                    params.forEach { (key, value) ->
                        when (key.uppercase()) {
                            "QUERY" -> viewModel.query = value
                            "GENRE" -> viewModel.genre = value
                            "SORT" -> viewModel.sort = value
                            "YEAR" -> viewModel.year = value.toIntOrNull()
                            "MONTH" -> viewModel.month = value.toIntOrNull()
                            "DURATION" -> viewModel.duration = value
                        }
                    }
                }
        }
    }

    if (showAdvancedSearchSheet) {
        AdvancedSearchSheet(
            viewModel = viewModel,
            onDismiss = { showAdvancedSearchSheet = false },
        )
    }

    SearchScreen(
        viewModel = viewModel,
        initialQuery = route.query,
        onBack = onBack,
        onOpenVideo = onNavigateToVideo,
        onOpenAdvancedSearch = { showAdvancedSearchSheet = true },
    )
}
