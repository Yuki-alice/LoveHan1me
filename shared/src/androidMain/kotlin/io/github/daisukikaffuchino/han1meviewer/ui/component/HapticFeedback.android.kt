package io.github.daisukikaffuchino.han1meviewer.ui.component

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

// Android：原 VibrationUtil 的默认语义（HapticFeedbackConstants.CONTEXT_CLICK）
@Composable
internal actual fun rememberHapticFeedback(): HapticFeedback {
    val view = LocalView.current
    return remember(view) {
        HapticFeedback { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    }
}
