package io.github.daisukikaffuchino.han1meviewer.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.daisukikaffuchino.han1meviewer.ui.player.DesktopMpvPlaybackEngine
import io.github.daisukikaffuchino.han1meviewer.ui.player.PlatformVideoSurface
import io.github.daisukikaffuchino.han1meviewer.ui.player.PlaybackRequest
import io.github.daisukikaffuchino.utils.LogUtil

/**
 * P5-2a 临时 spike 屏（收尾删除）：桌面 mpv 真播验证。
 *
 * 片源：`-Dhanime.spike.video=` 覆盖本地文件做离线测；默认播 Google 公开样片
 * （ExoPlayer demo 同款，顺带证明网络路径 + UA）。
 * 控制台逐秒打 state/pos（无头可验证），手动点暂停/seek 验证不死锁。
 */
private const val TAG = "MpvSpike"
private const val DEFAULT_SPIKE_VIDEO =
    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

@Composable
fun MpvSpikeScreen(onBack: () -> Unit) {
    val engine = remember { DesktopMpvPlaybackEngine() }
    val state by engine.state.collectAsState()

    LaunchedEffect(Unit) {
        val uri = System.getProperty("hanime.spike.video", DEFAULT_SPIKE_VIDEO)
        LogUtil.d(TAG, "spike load: $uri")
        engine.load(PlaybackRequest(uri = uri, title = "mpv spike"))
    }
    LaunchedEffect(Unit) {
        while (true) {
            val s = engine.state.value
            println("[spike] phase=${s.phase} playing=${s.isPlaying} pos=${s.positionMs}ms dur=${s.durationMs}ms err=${s.errorMessage}")
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                engine.release()
                onBack()
            }) { Text("← 返回（收尾删除本屏）") }
            Text(
                text = "phase=${state.phase} pos=${state.positionMs}/${state.durationMs}ms",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        state.errorMessage?.let {
            Text(text = "ERROR: $it", color = MaterialTheme.colorScheme.error)
        }
        PlatformVideoSurface(
            engine = engine,
            modifier = Modifier.fillMaxWidth().height(360.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Button(onClick = { engine.play() }) { Text("Play") }
            Button(onClick = { engine.pause() }) { Text("Pause") }
            Button(onClick = { engine.seekTo(state.positionMs + 10_000L) }) { Text("+10s") }
            Button(onClick = { engine.setPlaybackSpeed(2f) }) { Text("2x") }
        }
    }
}
