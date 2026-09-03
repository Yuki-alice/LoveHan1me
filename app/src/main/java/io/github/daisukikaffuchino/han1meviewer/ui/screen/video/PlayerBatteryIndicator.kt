package io.github.daisukikaffuchino.han1meviewer.ui.screen.video

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.daisukikaffuchino.han1meviewer.R

@Composable
internal fun PlayerBatteryIndicator(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        Icon(
            painter = painterResource(R.drawable.ic_battery_android_frame_full),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.84f),
            modifier = modifier.size(20.dp),
        )
        return
    }

    val context = LocalContext.current
    var iconResId by remember { mutableIntStateOf(R.drawable.ic_battery_android_frame_question) }

    DisposableEffect(context) {
        fun updateBatteryIcon(intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val percentage = if (level >= 0 && scale > 0) level * 100 / scale else -1
            iconResId = when {
                percentage < 0 -> R.drawable.ic_battery_android_frame_question
                status == BatteryManager.BATTERY_STATUS_CHARGING ->
                    R.drawable.ic_battery_android_frame_bolt
                status == BatteryManager.BATTERY_STATUS_FULL ->
                    R.drawable.ic_battery_android_frame_full
                percentage <= 15 -> R.drawable.ic_battery_android_frame_1
                percentage <= 30 -> R.drawable.ic_battery_android_frame_2
                percentage <= 45 -> R.drawable.ic_battery_android_frame_3
                percentage <= 60 -> R.drawable.ic_battery_android_frame_4
                percentage <= 75 -> R.drawable.ic_battery_android_frame_5
                percentage <= 90 -> R.drawable.ic_battery_android_frame_6
                else -> R.drawable.ic_battery_android_frame_full
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                updateBatteryIcon(intent)
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        updateBatteryIcon(stickyIntent)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Icon(
        painter = painterResource(iconResId),
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.84f),
        modifier = modifier.size(20.dp),
    )
}
