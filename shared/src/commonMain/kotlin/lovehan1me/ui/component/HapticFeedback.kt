package lovehan1me.ui.component

import androidx.compose.runtime.Composable
import lovehan1me.logic.SettingsRepository

/**
 * 触感反馈（P6b）：commonMain 抽象，替代原 `VibrationUtil.performHapticFeedback(view)`。
 * 开关沿用共享设置 SettingsRepository.hapticFeedbackEnabled（androidMain 内执行真实反馈）。
 */
class HapticFeedback internal constructor(private val block: () -> Unit) {
    operator fun invoke() {
        if (SettingsRepository.hapticFeedbackEnabled) block()
    }
}

@Composable
internal expect fun rememberHapticFeedback(): HapticFeedback
