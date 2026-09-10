package lovehan1me.utils

import android.view.HapticFeedbackConstants
import android.view.View
import lovehan1me.logic.SettingsRepository

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
