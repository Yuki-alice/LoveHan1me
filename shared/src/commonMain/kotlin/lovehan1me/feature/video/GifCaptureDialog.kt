package lovehan1me.feature.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import lovehan1me.Res
import lovehan1me.cancel
import lovehan1me.core.platform.MediaExportOutcome
import lovehan1me.core.platform.currentEpochMillis
import lovehan1me.core.platform.exportMediaAndShare
import lovehan1me.core.util.gif.GifCapturePolicy
import lovehan1me.core.util.gif.GifRecorder
import lovehan1me.gif_capture
import lovehan1me.gif_capture_duration
import lovehan1me.gif_capture_failed
import lovehan1me.gif_capture_generating
import lovehan1me.gif_capture_saved
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** 候选时长（毫秒）。超预算的项会被 [GifCapturePolicy] 复核掉。 */
private val DURATION_CHOICES_MS = listOf(2_000, 3_000, 5_000)

private const val GIF_MIME = "image/gif"

/**
 * 「片段转 GIF」对话框（M3-b）。
 *
 * 一次手势走完**选时长 → 逐帧生成（带进度）→ 保存并唤起系统分享**，
 * 中途不让用户选目录、也不做相册管理（与路线图 M3-c 同一取向）。
 *
 * ## 失败路径全部有提示、不崩
 * [GifRecorder.record] 把超限 / 抓帧失败 / 编码失败都收敛成结果类型，这里逐条映射成文案：
 * - **超内存预算的时长选项根本不会出现**（提前用 [GifCapturePolicy.plan] 过滤），
 *   于是用户点不到必然失败的按钮；
 * - 抓帧失败 → 报"已抓 N/M 帧"，便于区分"平台不支持"与"中途取不到"；
 * - 编码/落盘失败 → 原样带出底层消息。
 *
 * 选项上顺带显示**估算体积**：路线图把「限制时长」列为产品决策，
 * 那么"录多久会得到多大"就该让用户在点之前看到。
 *
 * @param startPositionMs 录制起点（一般是当前播放位置）
 * @param captureFrameAt 抓帧回调，由
 *        [lovehan1me.feature.player.ComposePlaybackController.grabFrameArgb] 提供
 */
@Composable
fun GifCaptureDialog(
    sourceWidth: Int,
    sourceHeight: Int,
    startPositionMs: Long,
    onDismiss: () -> Unit,
    captureFrameAt: suspend (positionMs: Long, targetWidth: Int, targetHeight: Int) -> IntArray?,
    limits: GifCapturePolicy.Limits = GifCapturePolicy.Limits(),
) {
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    val choices = remember(sourceWidth, sourceHeight, limits) {
        DURATION_CHOICES_MS.mapNotNull { ms ->
            val planned = GifCapturePolicy.plan(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                requestedDurationMs = ms,
                requestedFps = limits.maxFps,
                limits = limits,
            )
            (planned as? GifCapturePolicy.Result.Ok)?.let { ms to it.plan }
        }
    }

    fun start(durationMs: Int) {
        working = true
        resultMessage = null
        scope.launch {
            val outcome = GifRecorder.record(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                requestedDurationMs = durationMs,
                requestedFps = limits.maxFps,
                startPositionMs = startPositionMs,
                limits = limits,
                onProgress = { d, t -> done = d; total = t },
                captureFrameAt = captureFrameAt,
            )
            working = false
            // 协程里不能用 stringResource（@Composable），用挂起版 getString ——
            // 与 HomeSettingsRoute / buildWebViewVersionTip 的既有做法一致
            resultMessage = when (outcome) {
                is GifRecorder.Outcome.Success -> when (
                    val export = exportMediaAndShare(
                        bytes = outcome.bytes,
                        fileName = "LoveHan1me_${currentEpochMillis()}.gif",
                        mimeType = GIF_MIME,
                    )
                ) {
                    // Shared 与 SavedOnly 对用户都是"存好了"，区别只在有没有弹分享面板
                    is MediaExportOutcome.Shared ->
                        getString(Res.string.gif_capture_saved, export.location)

                    is MediaExportOutcome.SavedOnly ->
                        getString(Res.string.gif_capture_saved, export.location)

                    is MediaExportOutcome.Failed ->
                        getString(Res.string.gif_capture_failed, export.message)
                }

                is GifRecorder.Outcome.Rejected ->
                    getString(Res.string.gif_capture_failed, outcome.reason.name)

                is GifRecorder.Outcome.CaptureFailed ->
                    getString(
                        Res.string.gif_capture_failed,
                        "capture ${outcome.capturedFrames}/${outcome.expectedFrames}",
                    )

                is GifRecorder.Outcome.EncodeFailed ->
                    getString(Res.string.gif_capture_failed, outcome.message)
            }
        }
    }

    val message = resultMessage

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(Res.string.gif_capture)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    working -> {
                        Text(
                            stringResource(
                                Res.string.gif_capture_generating,
                                done.toString(),
                                total.toString(),
                            )
                        )
                        LinearProgressIndicator(
                            progress = { if (total > 0) done.toFloat() / total else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    message != null -> Text(message)

                    choices.isEmpty() ->
                        Text(stringResource(Res.string.gif_capture_failed, "no viable plan"))

                    else -> {
                        Text(
                            stringResource(Res.string.gif_capture_duration),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            choices.forEach { (durationMs, plan) ->
                                TextButton(onClick = { start(durationMs) }) {
                                    val mb = plan.estimatedFrameBytes / 1024 / 1024
                                    Text("${durationMs / 1000}s · ${mb}MB")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
