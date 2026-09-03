package io.github.daisukikaffuchino.utils

import android.view.HapticFeedbackConstants
import android.view.View
import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository

object VibrationUtil {
    fun performHapticFeedback(
        view: View,
        feedbackConstant: Int = HapticFeedbackConstants.CONTEXT_CLICK,
    ) {
        if (SettingsRepository.hapticFeedbackEnabled) {
            view.performHapticFeedback(feedbackConstant)
        }
    }
}
