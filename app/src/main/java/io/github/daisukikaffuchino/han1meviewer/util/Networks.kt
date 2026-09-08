package io.github.daisukikaffuchino.han1meviewer.util

import com.google.common.util.concurrent.ListenableFuture
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.home_error_connect
import io.github.daisukikaffuchino.han1meviewer.home_error_connection_interrupted
import io.github.daisukikaffuchino.han1meviewer.home_error_connection_reset
import io.github.daisukikaffuchino.han1meviewer.home_error_dns
import io.github.daisukikaffuchino.han1meviewer.home_error_forbidden
import io.github.daisukikaffuchino.han1meviewer.home_error_generic
import io.github.daisukikaffuchino.han1meviewer.home_error_not_found
import io.github.daisukikaffuchino.han1meviewer.home_error_server_unavailable
import io.github.daisukikaffuchino.han1meviewer.home_error_ssl
import io.github.daisukikaffuchino.han1meviewer.home_error_timeout
import org.jetbrains.compose.resources.StringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <R> ListenableFuture<R>.await(): R {
    // Fast path
    if (isDone) {
        try {
            return get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }
    return suspendCancellableCoroutine { cancellableContinuation ->
        addListener(
            {
                try {
                    cancellableContinuation.resume(get())
                } catch (throwable: Throwable) {
                    val cause = throwable.cause ?: throwable
                    when (throwable) {
                        is java.util.concurrent.CancellationException ->
                            cancellableContinuation.cancel(cause)

                        else -> cancellableContinuation.resumeWithException(cause)
                    }
                }
            },
            DirectExecutor
        )

        cancellableContinuation.invokeOnCancellation {
            cancel(false)
        }
    }
}

/**
 * Suspend extension that allows suspend [Call] inside coroutine.
 */
suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        })
        continuation.invokeOnCancellation { cancel() }
    }
}

/**
 * Run suspend catching
 *
 * @param block suspend block
 */
inline fun <R> runSuspendCatching(block: () -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (c: CancellationException) {
        throw c
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

private data object DirectExecutor : Executor {

    override fun execute(command: Runnable) {
        command.run()
    }
}

// M2：toNetworkErrorMessageRes 已下沉 shared（同包同名 util/NetworkError.kt，
// commonMain 无 java.net 类型故只保留关键字匹配）。本文件其余 OkHttp 辅助保留。
