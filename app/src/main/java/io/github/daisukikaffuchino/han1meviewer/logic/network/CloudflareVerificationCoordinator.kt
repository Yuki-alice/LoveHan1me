package io.github.daisukikaffuchino.han1meviewer.logic.network

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import io.github.daisukikaffuchino.han1meviewer.ui.activity.MainActivity
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.ACTION_OPEN_CLOUDFLARE_VERIFICATION
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.EXTRA_CLOUDFLARE_HOST
import io.github.daisukikaffuchino.han1meviewer.ui.navigation.main.EXTRA_CLOUDFLARE_URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Coalesces simultaneous challenges for the same host and gives every waiting request a
 * definitive result. A cancelled verification must not retry its original request without a
 * clearance cookie.
 */
object CloudflareVerificationCoordinator {

    private const val VERIFICATION_TIMEOUT_MINUTES = 5L

    private class Verification {
        val completed = CountDownLatch(1)

        @Volatile
        var succeeded = false
    }

    private val lock = Any()
    private val verifications = mutableMapOf<String, Verification>()

    fun verify(context: Context, url: String): Boolean {
        val host = url.toUri().host?.lowercase() ?: return false
        var shouldLaunch = false
        val verification = synchronized(lock) {
            verifications[host] ?: Verification().also {
                verifications[host] = it
                shouldLaunch = true
            }
        }

        if (shouldLaunch) {
            try {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .setAction(ACTION_OPEN_CLOUDFLARE_VERIFICATION)
                        .putExtra(EXTRA_CLOUDFLARE_URL, url)
                        .putExtra(EXTRA_CLOUDFLARE_HOST, host)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            } catch (_: Exception) {
                complete(host, succeeded = false)
            }
        }

        val succeeded = verification.completed.await(
            VERIFICATION_TIMEOUT_MINUTES,
            TimeUnit.MINUTES,
        ) && verification.succeeded
        if (!succeeded) {
            synchronized(lock) {
                if (verifications[host] === verification) {
                    verifications.remove(host)
                }
            }
        }
        return succeeded
    }

    fun complete(host: String, succeeded: Boolean) {
        val verification = synchronized(lock) { verifications.remove(host.lowercase()) } ?: return
        verification.succeeded = succeeded
        verification.completed.countDown()
    }
}
