package lovehan1me.feature.player

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import lovehan1me.data.database.dao.Han1meDatabaseContext
import lovehan1me.core.platform.CurrentActivityHolder
import lovehan1me.core.util.OrientationManager

// M3：原 `:app` VideoPlayerUi / VideoRouteHostScreen 内联的 Android-only 代码，原样归位。

actual fun isActiveNetworkMetered(): Boolean {
    val connectivityManager =
        Han1meDatabaseContext.appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager
    return connectivityManager?.isActiveNetworkMetered == true
}

@Composable
actual fun BindOrientationAutoFullscreen(
    enabled: Boolean,
    onLandscape: () -> Unit,
    onPortrait: () -> Unit,
) {
    if (!enabled) return
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val activity = CurrentActivityHolder.activity
        if (activity == null) {
            onDispose { }
        } else {
            val manager = OrientationManager(activity) { orientation ->
                if (orientation.isLandscape) onLandscape() else onPortrait()
            }
            lifecycleOwner.lifecycle.addObserver(manager)
            onDispose { lifecycleOwner.lifecycle.removeObserver(manager) }
        }
    }
}
