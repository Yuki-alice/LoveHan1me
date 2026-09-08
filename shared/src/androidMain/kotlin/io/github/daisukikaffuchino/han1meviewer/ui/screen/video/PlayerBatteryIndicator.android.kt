package io.github.daisukikaffuchino.han1meviewer.ui.screen.video

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

// M3：原 `:app` PlayerBatteryIndicator 的读取部分原样归位。
@Composable
actual fun rememberBatteryState(): BatteryState? {
    val context = LocalContext.current
    var state by remember { mutableStateOf<BatteryState?>(null) }

    DisposableEffect(context) {
        fun updateBatteryState(intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val percentage = if (level >= 0 && scale > 0) level * 100 / scale else -1
            state = BatteryState(
                levelPercent = percentage,
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING,
                isFull = status == BatteryManager.BATTERY_STATUS_FULL,
            )
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                updateBatteryState(intent)
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        updateBatteryState(stickyIntent)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    return state
}
