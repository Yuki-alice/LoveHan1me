package io.github.daisukikaffuchino.han1meviewer.logic.platform

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

actual @Composable
fun isLandscapeOrientation(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
