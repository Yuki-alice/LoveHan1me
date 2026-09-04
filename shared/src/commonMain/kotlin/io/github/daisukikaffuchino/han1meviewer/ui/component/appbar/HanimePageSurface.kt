package io.github.daisukikaffuchino.han1meviewer.ui.component.appbar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.daisukikaffuchino.han1meviewer.ui.theme.HanimeDefaults

/** Shared page content surface used by every non-home page shell. */
@Composable
fun HanimePageSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = HanimeDefaults.Colors.pageSurface,
        shape = HanimeDefaults.Corners.large,
        content = content,
    )
}
