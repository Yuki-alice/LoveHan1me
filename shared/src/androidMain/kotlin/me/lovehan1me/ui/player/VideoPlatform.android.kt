package me.lovehan1me.ui.player

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.net.ConnectivityManager
import android.os.Build
import android.util.StateSet
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import me.lovehan1me.logic.dao.Han1meDatabaseContext
import me.lovehan1me.logic.platform.CurrentActivityHolder
import me.lovehan1me.utils.OrientationManager

// M3：原 `:app` VideoPlayerUi / VideoRouteHostScreen 内联的 Android-only 代码，原样归位。

@Composable
actual fun CastRouteButton(
    contentDescription: String,
    modifier: Modifier,
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier.size(24.dp),
        factory = { ctx ->
            MediaRouteButton(ctx).also { button ->
                button.minimumWidth = 0
                button.minimumHeight = 0
                button.contentDescription = contentDescription
                CastButtonFactory.setUpMediaRouteButton(ctx, button)
                button.setRemoteIndicatorDrawable(createGoogleCastIndicator(ctx))
            }
        },
    )
}

actual fun Modifier.posterBlur(radiusPx: Float): Modifier = this.then(
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.graphicsLayer {
            renderEffect =
                RenderEffect
                    .createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
        }
    } else {
        Modifier
    }
)

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

private fun createGoogleCastIndicator(context: Context): Drawable = StateListDrawable().apply {
    addState(
        intArrayOf(android.R.attr.state_checked),
        whiteDrawable(context, androidx.media3.cast.R.drawable.media_route_button_connected),
    )
    addState(
        intArrayOf(android.R.attr.state_checkable),
        whiteDrawable(context, androidx.media3.cast.R.drawable.media_route_button_disconnected),
    )
    addState(
        StateSet.WILD_CARD,
        whiteDrawable(context, androidx.media3.cast.R.drawable.media_route_button_disconnected),
    )
}

private fun whiteDrawable(context: Context, drawableRes: Int): Drawable =
    requireNotNull(ContextCompat.getDrawable(context, drawableRes)).mutate().also { drawable ->
        DrawableCompat.setTint(drawable, android.graphics.Color.WHITE)
    }
