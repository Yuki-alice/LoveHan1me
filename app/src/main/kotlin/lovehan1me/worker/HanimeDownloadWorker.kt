package lovehan1me.worker

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.ParcelFileDescriptor
import lovehan1me.core.util.LogUtil
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import lovehan1me.DOWNLOAD_NOTIFICATION_CHANNEL
import lovehan1me.EMPTY_STRING
import lovehan1me.HFileManager
import lovehan1me.HFileManager.createVideoName
import lovehan1me.R
import lovehan1me.Res
import lovehan1me.download_error_cancelled
import lovehan1me.download_completed_s
import lovehan1me.download_error_connect
import lovehan1me.download_error_dns
import lovehan1me.download_error_file_info
import lovehan1me.download_error_network
import lovehan1me.download_error_range_not_supported
import lovehan1me.download_error_storage
import lovehan1me.download_error_timeout
import lovehan1me.download_failed_s_exists
import lovehan1me.download_task_completed
import lovehan1me.download_task_failed
import lovehan1me.download_task_failed_s_reason_s
import lovehan1me.download_task_retrying
import lovehan1me.download_task_retrying_s_reason_s
import lovehan1me.downloading_s
import lovehan1me.this_data_exists
import lovehan1me.unknown_download_error
import lovehan1me.logic.DatabaseRepo
import lovehan1me.data.database.entity.download.DownloadGroupEntity
import lovehan1me.data.database.entity.download.HanimeDownloadEntity
import lovehan1me.data.network.ServiceCreator
import lovehan1me.core.domain.state.DownloadState
import lovehan1me.core.util.HImageMeower
import lovehan1me.core.util.SafFileManager
import lovehan1me.core.util.await
import lovehan1me.core.util.createFileIfNotExists
import lovehan1me.core.util.saveTo
import lovehan1me.core.util.SonnerToast
import lovehan1me.core.util.toastText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.internal.closeQuietly
import org.jetbrains.compose.resources.getString
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.SocketException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2022/08/06 006 11:42
 */
class HanimeDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams), WorkerMixin {

    data class Args(
        val quality: String?,
        val downloadUrl: String?,
        val videoType: String?,
        val hanimeName: String,
        val videoCode: String,
        val coverUrl: String,
        val groupId: Int = DownloadGroupEntity.DEFAULT_GROUP_ID,
    ) {
        companion object {
            fun fromEntity(entity: HanimeDownloadEntity): Args {
                return Args(
                    quality = entity.quality,
                    downloadUrl = entity.videoUrl,
                    videoType = entity.suffix,
                    hanimeName = entity.title,
                    videoCode = entity.videoCode,
                    coverUrl = entity.coverUrl,
                    groupId = entity.groupId,
                )
            }
        }
    }

    companion object {
        const val TAG = "HanimeDownloadWorker"

        const val RESPONSE_INTERVAL = 500L

        const val BACKOFF_DELAY = 10_000L

        private const val MAX_STREAM_RETRY_COUNT = 3
        private const val MAX_WORK_RETRY_COUNT = 3

        const val FAST_PATH_CANCEL = "fast_path_cancel"
        const val DELETE = "delete"
        const val QUALITY = "quality"
        const val DOWNLOAD_URL = "download_url"
        const val VIDEO_TYPE = "video_type"
        const val HANIME_NAME = "hanime_name"
        const val VIDEO_CODE = "video_code"
        const val COVER_URL = "cover_url"
        const val GROUP_ID = "group_id"
        const val REDOWNLOAD = "redownload"
        const val IN_WAITING_QUEUE = "in_waiting_queue"
        // const val RELEASE_DATE = "release_date"
        // const val COVER_DOWNLOAD = "cover_download"

        const val PROGRESS = "progress"
        // const val FAILED_REASON = "failed_reason"

        private val CONTENT_RANGE_LENGTH_REGEX = Regex("/([0-9]+)$")

        /**
         * 方便统一管理下载 Worker 的创建
         */
        inline fun build(
            constraintsRequired: Boolean = true,
            action: OneTimeWorkRequest.Builder.() -> Unit = {}
        ): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build()
            return OneTimeWorkRequestBuilder<HanimeDownloadWorker>()
                .addTag(TAG)
                .let { builder ->
                    if (constraintsRequired) {
                        builder.setConstraints(constraints)
                    } else {
                        builder
                    }
                }.setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    BACKOFF_DELAY, TimeUnit.MILLISECONDS
                ).apply(action).build()
        }

        fun getRunningWorkInfoCount(context: Context): Flow<Int> {
            return WorkManager.getInstance(context)
                .getWorkInfosByTagFlow(TAG)
                .map { workInfos ->
                    workInfos.count {
                        it.state == WorkInfo.State.RUNNING
                    }
                }.distinctUntilChanged()
        }
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    private val hanimeName by inputData(HANIME_NAME, EMPTY_STRING)
    private val downloadUrl by inputData(DOWNLOAD_URL, EMPTY_STRING)
    private val videoType by inputData(VIDEO_TYPE, HFileManager.DEF_VIDEO_TYPE)
    private val quality by inputData(QUALITY, EMPTY_STRING)
    private val videoCode by inputData(VIDEO_CODE, EMPTY_STRING)
    private val coverUrl by inputData(COVER_URL, EMPTY_STRING)
    private val groupId by inputData(GROUP_ID, DownloadGroupEntity.DEFAULT_GROUP_ID)

    private val fastPathCancel by inputData(FAST_PATH_CANCEL, false)
    private val shouldDelete by inputData(DELETE, false)
    private val shouldRedownload by inputData(REDOWNLOAD, false)
    private val isInWaitingQueue by inputData(IN_WAITING_QUEUE, false)

    private val downloadId = Random.nextInt()

    /**
     * 结果通知（成功/失败/重试/已存在）专用 id：必须与前台进度通知的 [downloadId]
     * 区分，否则 Worker 结束时 stopForeground 连带清掉同 id 的结果通知
     * （M8 后续通知验证：完成通知闪现即消失）。
     */
    private val resultNotificationId = downloadId + 1

    private val mainScope = CoroutineScope(Dispatchers.Main.immediate)
    private val dbScope = CoroutineScope(Dispatchers.IO)

    override suspend fun doWork(): Result {
        if (fastPathCancel) return Result.success()
        setForeground(createForegroundInfo())
        return download()
    }

    private suspend fun createNewRaf(file: File): HanimeDownloadEntity? {
        return withContext(Dispatchers.IO) {
            var raf: RandomAccessFile? = null
            try {
                // SAF 优先
                val safUri = SafFileManager.getDownloadVideoFileUri(context, videoCode, createVideoName(hanimeName, quality, videoType))
                LogUtil.i(TAG,safUri.toString())
                if (safUri != null) {
                    context.contentResolver.openFileDescriptor(safUri, "rw")?.closeQuietly()
                } else {
                    file.createFileIfNotExists()
                    raf = RandomAccessFile(file, "rwd")
                }

                val len = fetchContentLength() ?: return@withContext null
                if (len > 0) {
                    // 创建数据库记录
                    val entity = HanimeDownloadEntity(
                        groupId = groupId,
                        coverUrl = coverUrl,
                        coverUri = null,
                        title = hanimeName,
                        addDate = System.currentTimeMillis(),
                        videoCode = videoCode,
                        videoUri = safUri?.toString() ?: file.toUri().toString(),
                        quality = quality,
                        videoUrl = downloadUrl,
                        length = len,
                        downloadedLength = 0,
                        state = DownloadState.Queued
                    )
                    DatabaseRepo.HanimeDownload.insert(entity)
                    // 预写入长度（只有 File 支持）
                    raf?.setLength(len)
                    return@withContext entity
                }
            } catch (e: Exception) {
                if (e is CancellationException || e.isStoppedCancellation() || e.isRetryableNetworkError()) {
                    throw e
                }
                e.printStackTrace()
                if (file.exists() && file.length() == 0L) {
                    dbScope.launch {
                        HFileManager.getDownloadVideoFolder(context, videoCode).deleteRecursively()
                    }
                }
            } finally {
                raf?.closeQuietly()
            }
            null
        }
    }

    private suspend fun fetchContentLength(): Long? {
        requestContentLength(useHead = true)?.let { return it }
        return requestContentLength(useHead = false)
    }

    private suspend fun requestContentLength(useHead: Boolean): Long? {
        val requestBuilder = Request.Builder().url(downloadUrl)
        val request = if (useHead) {
            requestBuilder.head().build()
        } else {
            requestBuilder.header("Range", "bytes=0-0").get().build()
        }
        return try {
            ServiceCreator.downloadClient.newCall(request).await().use { response ->
                if (!response.isSuccessful) return@use null
                if (useHead) {
                    response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
                        ?: response.contentLengthFromContentRange()
                } else {
                    response.contentLengthFromContentRange()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isRetryableNetworkError()) throw e
            null
        }
    }

    private fun Response.contentLengthFromContentRange(): Long? {
        return header("Content-Range")
            ?.let { CONTENT_RANGE_LENGTH_REGEX.find(it)?.groupValues?.getOrNull(1) }
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
    }

    private suspend fun download(): Result {
        return withContext(Dispatchers.IO) {
            val file = HFileManager.getDownloadVideoFile(
                context = context, title = hanimeName, quality = quality, suffix = videoType, videoCode = videoCode
            )
            val safUri = SafFileManager.getDownloadVideoFileUri(context, videoCode, createVideoName(hanimeName, quality, videoType))
            // 检查是否需要重下载
            if (shouldRedownload || shouldDelete) {
                HFileManager.getDownloadVideoFolder(context, videoCode).deleteRecursively()
                DatabaseRepo.HanimeDownload.delete(videoCode)
                if (shouldDelete) {
                    return@withContext Result.success()
                }
            }
            var entity = try {
                DatabaseRepo.HanimeDownload.find(videoCode, quality) ?: run {
                    createNewRaf(file)
                    DatabaseRepo.HanimeDownload.find(videoCode, quality)
                        ?: return@withContext run {
                            LogUtil.d(TAG, "entity is null, create new raf failed")
                            val reason = getString(Res.string.download_error_file_info)
                            showFailureNotification(reason)
                            mainScope.launch {
                                SonnerToast.error(
                                    getString(Res.string.download_task_failed_s_reason_s, hanimeName, reason)
                                )
                            }
                            Result.failure(workDataOf(DownloadState.STATE to DownloadState.Failed.mask))
                        }
                }
            } catch (e: Exception) {
                if (e.isRetryableNetworkError() && runAttemptCount < MAX_WORK_RETRY_COUNT) {
                    DatabaseRepo.HanimeDownload.find(videoCode, quality)?.let {
                        DatabaseRepo.HanimeDownload.update(it.copy(state = DownloadState.Queued))
                    }
                    return@withContext Result.retry()
                }
                throw e
            }

            if (entity.downloadedLength >= entity.length && entity.length > 0) {
                DatabaseRepo.HanimeDownload.update(entity.copy(state = DownloadState.Finished))
                showSuccessNotification()
                return@withContext Result.success(
                    workDataOf(DownloadState.STATE to DownloadState.Finished.mask)
                )
            }

            if (entity.downloadedLength < 0 || entity.downloadedLength > entity.length) {
                entity = entity.copy(downloadedLength = 0, state = DownloadState.Queued)
                DatabaseRepo.HanimeDownload.update(entity)
            }

            if (entity.coverUri == null) {
                updateCoverImage(entity)
            }
            if (isInWaitingQueue) {
                DatabaseRepo.HanimeDownload.update(
                    entity.copy(state = DownloadState.Queued)
                )
                return@withContext Result.success()
            }

            var downloadedLength = entity.downloadedLength
            val needRange = downloadedLength > 0
            var raf: RandomAccessFile? = null
            var safPfd: ParcelFileDescriptor? = null
            var safChannel: FileChannel? = null
            var response: Response? = null
            var body: ResponseBody? = null
            var bodyStream: InputStream? = null

            var result: Result = Result.failure(
                workDataOf(DownloadState.STATE to DownloadState.Failed.mask)
            )
            var shouldRetry = false

            try {
                if (safUri != null) {
                    safPfd = context.contentResolver.openFileDescriptor(safUri, "rw")
                    safChannel = safPfd?.fileDescriptor?.let { FileOutputStream(it).channel }
                        ?: throw IOException("Open SAF file failed")
                    if (downloadedLength > safChannel.size()) {
                        downloadedLength = safChannel.size()
                    }
                    safChannel.position(downloadedLength)
                } else {
                    raf = RandomAccessFile(file, "rwd")
                    if (needRange) raf.seek(downloadedLength)
                }
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var delayTime = 0L
                var retryCount = 0

                while (downloadedLength < entity.length) {
                    val requestNeedRange = downloadedLength > 0
                    val requestBuilder = Request.Builder().url(downloadUrl).get()
                    if (requestNeedRange) requestBuilder.header("Range", "bytes=$downloadedLength-")
                    val request = requestBuilder.build()
                    response = ServiceCreator.downloadClient.newCall(request).await()
                    val canWrite = (requestNeedRange && response.code == 206) || (!requestNeedRange && response.isSuccessful)
                    if (!canWrite) {
                        val reason = response.toDownloadErrorMessage(requestNeedRange)
                        showFailureNotification(reason)
                        mainScope.launch {
                            SonnerToast.error(getString(Res.string.download_task_failed_s_reason_s, hanimeName, reason))
                        }
                        result = Result.failure(workDataOf(DownloadState.STATE to DownloadState.Failed.mask))
                        return@withContext result
                    }

                    body = response.body
                    val responseBody = body
                    bodyStream = responseBody.byteStream()
                    var len: Int = bodyStream.read(buffer)

                    try {
                        while (len != -1) {
                            if (raf != null) {
                                raf.write(buffer, 0, len)
                            } else if (safChannel != null) {
                                safChannel.writeFully(buffer, len)
                            }
                            downloadedLength += len

                            if (System.currentTimeMillis() - delayTime > RESPONSE_INTERVAL) {
                                val progress = (downloadedLength * 100 / entity.length).coerceAtMost(100)
                                setProgress(workDataOf(PROGRESS to progress.toInt()))
                                updateDownloadNotification(progress.toInt())
                                DatabaseRepo.HanimeDownload.update(
                                    entity.copy(downloadedLength = downloadedLength,
                                        state = DownloadState.Downloading
                                    )
                                )
                                delayTime = System.currentTimeMillis()
                            }
                            len = bodyStream.read(buffer)
                        }
                    } catch (e: IOException) {
                        if (!e.isStreamResetCancel() || retryCount >= MAX_STREAM_RETRY_COUNT) {
                            throw e
                        }
                        retryCount++
                        response.closeQuietly()
                        body.closeQuietly()
                        bodyStream.closeQuietly()
                        response = null
                        body = null
                        bodyStream = null
                        continue
                    }

                    break
                }

                if (downloadedLength < entity.length) {
                    throw IOException("Download incomplete: $downloadedLength/${entity.length}")
                }

                showSuccessNotification()
                result = Result.success(
                    workDataOf(DownloadState.STATE to DownloadState.Finished.mask)
                )

            } catch (e: Exception) {
                result = if (e is CancellationException || e.isStoppedCancellation()) {
                    cancelDownloadNotification()
                    mainScope.launch { SonnerToast.info(getString(Res.string.download_error_cancelled)) }
                    Result.success(
                        workDataOf(DownloadState.STATE to DownloadState.Paused.mask)
                    )
                } else if (e.isRetryableNetworkError() && runAttemptCount < MAX_WORK_RETRY_COUNT) {
                    val reason = e.toDownloadErrorMessage()
                    showRetryNotification(reason)
                    mainScope.launch {
                        SonnerToast.warning(getString(Res.string.download_task_retrying_s_reason_s, hanimeName, reason))
                    }
                    shouldRetry = true
                    Result.retry()
                } else {
                    val reason = e.toDownloadErrorMessage()
                    showFailureNotification(reason)
                    e.printStackTrace()
                    mainScope.launch {
                        SonnerToast.error(getString(Res.string.download_task_failed_s_reason_s, hanimeName, reason))
                    }
                    Result.failure(
                        workDataOf(DownloadState.STATE to DownloadState.Failed.mask)
                    )
                }
            } finally {
                val state = DownloadState.from(
                    result.outputData.getInt(DownloadState.STATE, DownloadState.Unknown.mask)
                )
                DatabaseRepo.HanimeDownload.update(
                    entity.copy(
                        state = if (shouldRetry) DownloadState.Queued else state,
                        downloadedLength = downloadedLength
                    )
                )
                raf?.closeQuietly()
                safChannel?.closeQuietly()
                safPfd?.closeQuietly()
                response?.closeQuietly()
                body?.closeQuietly()
                bodyStream?.closeQuietly()
            }
            return@withContext result
        }
    }

    private fun IOException.isStreamResetCancel(): Boolean {
        return message?.contains("stream was reset: CANCEL", ignoreCase = true) == true
    }

    private fun Exception.isStoppedCancellation(): Boolean {
        return isStopped && this is IOException && message.equals("Canceled", ignoreCase = true)
    }

    private fun Exception.isRetryableNetworkError(): Boolean {
        return this is UnknownHostException ||
                this is SocketTimeoutException ||
                this is ConnectException ||
                this is SocketException ||
                (this is IOException && message.equals("Canceled", ignoreCase = true).not())
    }

    private suspend fun Exception.toDownloadErrorMessage(): String {
        return when (this) {
            is UnknownHostException -> getString(Res.string.download_error_dns)
            is SocketTimeoutException -> getString(Res.string.download_error_timeout)
            is ConnectException -> getString(Res.string.download_error_connect)
            is SocketException -> getString(Res.string.download_error_network)
            is IOException -> {
                val rawMessage = message.orEmpty()
                when {
                    rawMessage.contains("No space", ignoreCase = true) ||
                            rawMessage.contains("Permission", ignoreCase = true) ||
                            rawMessage.contains("Open SAF file failed", ignoreCase = true) -> {
                        getString(Res.string.download_error_storage)
                    }
                    rawMessage.contains("Download incomplete", ignoreCase = true) -> {
                        getString(Res.string.download_error_network)
                    }
                    else -> getString(Res.string.download_error_network)
                }
            }
            else -> localizedMessage?.takeIf { it.isNotBlank() }
                ?: getString(Res.string.unknown_download_error)
        }
    }

    private suspend fun Response.toDownloadErrorMessage(requestNeedRange: Boolean): String {
        return when {
            requestNeedRange && code == 416 -> {
                getString(Res.string.download_error_range_not_supported)
            }
            requestNeedRange -> getString(Res.string.download_error_range_not_supported)
            code in 500..599 -> getString(Res.string.download_error_network)
            else -> message.takeIf { it.isNotBlank() } ?: getString(Res.string.unknown_download_error)
        }
    }

    private fun FileChannel.writeFully(buffer: ByteArray, length: Int) {
        val byteBuffer = ByteBuffer.wrap(buffer, 0, length)
        while (byteBuffer.hasRemaining()) {
            write(byteBuffer)
        }
    }

    private fun CoroutineScope.updateCoverImage(entity: HanimeDownloadEntity) {
        launch {
            val imgRes = HImageMeower.execute(entity.coverUrl)
            val (os, uri) = SafFileManager.openOutputStreamForCover(
                context, entity.videoCode, entity.title
            )
            val isSuccess = os?.use { out -> imgRes.drawable?.saveTo(out) == true } ?: false
            if (isSuccess && uri != null) {
                val coverUriStr = uri.toString()
                withContext(Dispatchers.IO) {
                    DatabaseRepo.HanimeDownload.update(
                        entity.copy(coverUri = coverUriStr)
                    )
                }
                entity.coverUri = coverUriStr
            }
        }
    }

    private suspend fun createDownloadNotification(progress: Int = 0): Notification {
        return NotificationCompat.Builder(context, DOWNLOAD_NOTIFICATION_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher_new)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(getString(Res.string.downloading_s, hanimeName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentText("$progress%")
            .setProgress(100, progress, false)
            .build()
    }

    private fun cancelDownloadNotification() {
        notificationManager.cancel(downloadId)
    }

    @SuppressLint("MissingPermission")
    private suspend fun updateDownloadNotification(progress: Int) {
        notificationManager.notify(downloadId, createDownloadNotification(progress))
    }

    private suspend fun createForegroundInfo(progress: Int = 0): ForegroundInfo {
        val notification = createDownloadNotification(progress)
        return ForegroundInfo(
            downloadId, notification,
            // #issue-34: 這裡的參數是為了讓 Android 14 以上的系統可以正常顯示前景通知
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun showSuccessNotification() {
        notificationManager.notify(
            resultNotificationId, NotificationCompat.Builder(context, DOWNLOAD_NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_check_circle)
                .setContentTitle(getString(Res.string.download_task_completed))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentText(getString(Res.string.download_completed_s, hanimeName))
                .build()
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun showFileExistsFailureNotification(fileName: String) {
        notificationManager.notify(
            resultNotificationId, NotificationCompat.Builder(context, DOWNLOAD_NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_cancel_circle)
                .setContentTitle(getString(Res.string.this_data_exists))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentText(getString(Res.string.download_failed_s_exists, fileName))
                .build()
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun showFailureNotification(errMsg: String? = null) {
        notificationManager.notify(
            resultNotificationId, NotificationCompat.Builder(context, DOWNLOAD_NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_cancel_circle)
                .setContentTitle(getString(Res.string.download_task_failed))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentText(
                    getString(
                        Res.string.download_task_failed_s_reason_s,
                        hanimeName, errMsg ?: getString(Res.string.unknown_download_error)
                    )
                )
                .build()
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun showRetryNotification(reason: String) {
        notificationManager.notify(
            resultNotificationId, NotificationCompat.Builder(context, DOWNLOAD_NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(getString(Res.string.download_task_retrying))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentText(
                    getString(
                        Res.string.download_task_retrying_s_reason_s,
                        hanimeName, reason
                    )
                )
                .build()
        )
    }
}
