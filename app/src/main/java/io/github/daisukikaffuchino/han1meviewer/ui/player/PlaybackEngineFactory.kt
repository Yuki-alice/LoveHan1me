package io.github.daisukikaffuchino.han1meviewer.ui.player

import android.content.Context

object PlaybackEngineFactory {
    fun create(
        context: Context,
        kernel: PlayerKernel,
        allowCast: Boolean = true,
    ): PlaybackEngine {
        val localEngine = when (kernel) {
        PlayerKernel.MediaPlayer -> SystemPlaybackEngine(context)
        PlayerKernel.ExoPlayer -> ExoPlaybackEngine(context)
        PlayerKernel.MpvPlayer -> MpvPlaybackEngine(context)
        }
        return if (allowCast) {
            CastPlaybackEngine.createOrLocal(context, localEngine)
        } else {
            localEngine
        }
    }
}
