/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.top

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import lovehan1me.video.player.ui.support.TextWithBorder
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Composable
fun SystemTime() {
    var time by remember { mutableStateOf(formatTime(Clock.System.now())) }

    LaunchedEffect(Unit) {
        while (true) {
            time = formatTime(Clock.System.now())
            delay(1.seconds)
        }
    }

    TextWithBorder(
        text = time,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun formatTime(now: Instant): String {
    val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    return "$hour:$minute"
}
