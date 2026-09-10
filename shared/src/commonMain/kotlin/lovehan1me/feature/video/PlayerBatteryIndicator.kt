package lovehan1me.feature.video

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.ic_battery_android_frame_question
import lovehan1me.ic_battery_android_frame_full
import lovehan1me.ic_battery_android_frame_bolt
import lovehan1me.ic_battery_android_frame_6
import lovehan1me.ic_battery_android_frame_5
import lovehan1me.ic_battery_android_frame_4
import lovehan1me.ic_battery_android_frame_3
import lovehan1me.ic_battery_android_frame_2
import lovehan1me.ic_battery_android_frame_1

/**
 * M3：自 `:app` 下沉（电量图标映射逻辑不变；电量读取经 [rememberBatteryState]）。
 * 不支持的平台返回 null → 不显示图标（桌面本就不需要）。
 */
data class BatteryState(
    val levelPercent: Int,
    val isCharging: Boolean,
    val isFull: Boolean,
)

@Composable
expect fun rememberBatteryState(): BatteryState?

@Composable
internal fun PlayerBatteryIndicator(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        Icon(
            painter = painterResource(Res.drawable.ic_battery_android_frame_full),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.84f),
            modifier = modifier.size(20.dp),
        )
        return
    }

    val state = rememberBatteryState() ?: return
    var iconResId by remember(state) { mutableStateOf(batteryIconFor(state)) }

    Icon(
        painter = painterResource(iconResId),
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.84f),
        modifier = modifier.size(20.dp),
    )
}

private fun batteryIconFor(state: BatteryState): DrawableResource {
    val percentage = state.levelPercent
    return when {
        percentage < 0 -> Res.drawable.ic_battery_android_frame_question
        state.isCharging -> Res.drawable.ic_battery_android_frame_bolt
        state.isFull -> Res.drawable.ic_battery_android_frame_full
        percentage <= 15 -> Res.drawable.ic_battery_android_frame_1
        percentage <= 30 -> Res.drawable.ic_battery_android_frame_2
        percentage <= 45 -> Res.drawable.ic_battery_android_frame_3
        percentage <= 60 -> Res.drawable.ic_battery_android_frame_4
        percentage <= 75 -> Res.drawable.ic_battery_android_frame_5
        percentage <= 90 -> Res.drawable.ic_battery_android_frame_6
        else -> Res.drawable.ic_battery_android_frame_full
    }
}
