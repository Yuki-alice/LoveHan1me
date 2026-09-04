package io.github.daisukikaffuchino.han1meviewer.ui.component

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import io.github.daisukikaffuchino.han1meviewer.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.usage_notice_accept
import io.github.daisukikaffuchino.han1meviewer.usage_notice_accept_countdown
import io.github.daisukikaffuchino.han1meviewer.usage_notice_content
import io.github.daisukikaffuchino.han1meviewer.usage_notice_decline
import io.github.daisukikaffuchino.han1meviewer.usage_notice_title
import io.github.daisukikaffuchino.utils.isDebugBuild
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun UsageNoticeDialog(
    visible: Boolean,
    onAccepted: () -> Unit,
    onDeclined: () -> Unit,
) {
    if (!visible) return

    val requiredSeconds = if (isDebugBuild()) 5 else 20
    var remainingSeconds by remember { mutableIntStateOf(requiredSeconds) }
    var isResumed by remember { mutableStateOf(true) }
    var resetVersion by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    remainingSeconds = requiredSeconds
                    isResumed = true
                    resetVersion++
                }

                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    remainingSeconds = requiredSeconds
                    isResumed = false
                    resetVersion++
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(visible, isResumed, resetVersion) {
        while (visible && isResumed && remainingSeconds > 0) {
            delay(1_000L.milliseconds)
            remainingSeconds--
        }
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(Res.string.usage_notice_title)) },
        text = {
            Text(
                text = stringResource(Res.string.usage_notice_content),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                textAlign = TextAlign.Start,
            )
        },
        confirmButton = {
            TextButton(
                enabled = remainingSeconds == 0,
                onClick = onAccepted,
            ) {
                Text(
                    text = if (remainingSeconds == 0) {
                        stringResource(Res.string.usage_notice_accept)
                    } else {
                        stringResource(Res.string.usage_notice_accept_countdown, remainingSeconds)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDeclined) {
                Text(stringResource(Res.string.usage_notice_decline))
            }
        },
    )
}
