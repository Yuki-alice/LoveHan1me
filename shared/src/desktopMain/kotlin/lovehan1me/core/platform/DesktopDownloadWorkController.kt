package lovehan1me.core.platform

import lovehan1me.core.util.LogUtil
import lovehan1me.DESKTOP_USER_AGENT
import lovehan1me.USER_AGENT
import lovehan1me.logic.SettingsRepository
import lovehan1me.data.database.dao.Han1meDatabases
import lovehan1me.data.database.entity.download.DownloadGroupEntity
import lovehan1me.data.database.entity.download.HanimeDownloadEntity
import lovehan1me.core.domain.model.HanimeVideo
import lovehan1me.core.domain.state.DownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * M6-2：桌面下载引擎（B 站式体验的核心执行器）。
 *
 * - Room 库是唯一事实源：任务/进度全部落库，UI 直接观察 DB 流；
 * - 协程队列 + [Semaphore] 并发上限，进程重启经 [initialize] 从 DB 恢复未完成任务；
 * - OkHttp 流式下载：`Range: bytes=downloaded-` 断点续传（对齐 Android Worker 的
 *   RandomAccessFile + seek 行为），每秒把 `downloadedLength` 写回 DB；
 * - UA 与播放层一致（站点直链有防盗链，Referer 取当前站点）；
 * - 落盘目录：`SettingsRepository.safDownloadPath`（桌面复用该设置语义=下载目录），
 *   未配置时默认 `~/Han1meViewer/downloads`；结构 `<dir>/<videoCode>/<title> [<quality>].<suffix>`
 *   与 Android 的 getDownloadVideoFolder 对齐，删除逻辑直接删目录。
 */
object DesktopDownloadWorkController : DownloadWorkController {

    private const val TAG = "DesktopDownload"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    @Volatile
    private var maxConcurrent = 2

    @Volatile
    private var semaphore = Semaphore(maxConcurrent)

    /** 引擎活性（设置页/调试用；NoOp 平台恒 0） */
    private val activeCount = MutableStateFlow(0)

    override fun prune() {
        // 桌面无 WorkManager 尾账，no-op
    }

    override suspend fun initialize() {
        // 全新桌面库按 v5 直接建表不走 Migration4To5，download_groups 为空会导致外键拒绝
        runCatching { Han1meDatabases.download.downloadGroupDao.insertDefaultGroup() }
            .onFailure { LogUtil.w(TAG, "insertDefaultGroup failed in initialize", it) }
        runCatching {
            Han1meDatabases.download.hanimeDownloadDao.loadAllDownloadingHanimeOnce()
                .filter { it.state == DownloadState.Downloading || it.state == DownloadState.Queued }
                .forEach { startJob(it) }
        }.onFailure { LogUtil.e(TAG, "resume queue failed", it) }
    }

    override fun runningCount(): Flow<Int> = activeCount

    override fun updateDownloadLimit(count: Int) {
        maxConcurrent = count.coerceAtLeast(1)
        // Semaphore 无法扩容：仅对新任务的许可语义生效（下次重启重建）
    }

    // ── 发起下载（视频页「下载」按钮 → 选清晰度）──

    override suspend fun addTask(
        video: HanimeVideo,
        videoCode: String,
        quality: String?,
        groupId: Int,
        redownload: Boolean,
    ) = withContext(Dispatchers.IO) {
        val q = quality ?: video.videoUrls.keys.firstOrNull() ?: return@withContext
        val link = video.videoUrls[q]?.link ?: return@withContext
        val suffix = video.videoUrls[q]?.suffix ?: "mp4"
        val db = Han1meDatabases.download
        val dao = db.hanimeDownloadDao

        // 全新桌面库按 v5 直接建表不走 Migration4To5，download_groups 为空会导致外键拒绝
        db.downloadGroupDao.insertDefaultGroup()

        if (redownload) {
            dao.delete(videoCode, q)
        } else if (dao.find(videoCode, q) != null) {
            return@withContext
        }

        // 防御：调用方传入的 groupId 对应分组可能已被删除，降级为默认分组
        val safeGroupId = if (db.downloadGroupDao.getGroupById(groupId) != null) {
            groupId
        } else {
            LogUtil.w(TAG, "groupId=$groupId not found, fallback to DEFAULT_GROUP_ID")
            DownloadGroupEntity.DEFAULT_GROUP_ID
        }

        val entity = HanimeDownloadEntity(
            groupId = safeGroupId,
            coverUrl = video.coverUrl,
            title = video.title,
            addDate = Instant.now().toEpochMilli(),
            videoCode = videoCode,
            videoUri = "local://$videoCode",
            coverUri = null,
            quality = q,
            videoUrl = link,
            length = 0L,
            downloadedLength = 0L,
            state = DownloadState.Queued,
        )
        dao.insert(entity)
        startJob(entity)
    }

    override fun pauseTask(entity: HanimeDownloadEntity) {
        jobs.remove(taskKey(entity))?.cancel()
        scope.launch {
            Han1meDatabases.download.hanimeDownloadDao.find(entity.videoCode, entity.quality)
                ?.takeIf { it.state == DownloadState.Downloading || it.state == DownloadState.Queued }
                ?.let {
                    Han1meDatabases.download.hanimeDownloadDao.update(
                        it.copy(state = DownloadState.Paused),
                    )
                }
        }
    }

    override fun resumeTask(entity: HanimeDownloadEntity) {
        scope.launch {
            Han1meDatabases.download.hanimeDownloadDao.update(entity.copy(state = DownloadState.Queued))
            startJob(entity)
        }
    }

    override fun deleteTask(entity: HanimeDownloadEntity) {
        jobs.remove(taskKey(entity))?.cancel()
        scope.launch {
            Han1meDatabases.download.hanimeDownloadDao.delete(entity.videoCode, entity.quality)
            videoFile(entity).parentFile?.deleteRecursively()
        }
    }

    override fun deleteVideoFolder(videoCode: String) {
        File(downloadDir(), videoCode).deleteRecursively()
    }

    override suspend fun importDownloaded(): Boolean = false

    // ── 内部：执行器 ──

    private fun taskKey(entity: HanimeDownloadEntity) = "${entity.videoCode}|${entity.quality}"

    private fun downloadDir(): File {
        val configured = SettingsRepository.current.safDownloadPath
        return File(configured?.takeIf { it.isNotBlank() } ?: System.getProperty("user.home"))
            .let { if (it.name == "downloads") it else File(it, "Han1meViewer/downloads") }
    }

    private fun sanitizeFileName(name: String) =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120).ifBlank { "video" }

    private fun videoFile(entity: HanimeDownloadEntity): File = File(
        File(downloadDir(), entity.videoCode),
        "${sanitizeFileName(entity.title)} [${entity.quality}].${entity.suffix}",
    )

    private fun startJob(entity: HanimeDownloadEntity) {
        val key = taskKey(entity)
        if (jobs.containsKey(key)) return
        jobs[key] = scope.launch {
            semaphore.withPermit {
                runDownload(entity)
            }
        }
    }

    private suspend fun runDownload(entity0: HanimeDownloadEntity) {
        val dao = Han1meDatabases.download.hanimeDownloadDao
        val entity = dao.find(entity0.videoCode, entity0.quality) ?: return
        val file = videoFile(entity)
        file.parentFile?.mkdirs()

        dao.update(entity.copy(state = DownloadState.Downloading))
        activeCount.value = jobs.size

        var downloaded = entity.downloadedLength
        if (downloaded < 0 || (entity.length in 1 until downloaded)) downloaded = 0

        val request = Request.Builder()
            .url(entity.videoUrl)
            .header("User-Agent", USER_AGENT)
            .header("Referer", SettingsRepository.baseUrl)
            .apply { if (downloaded > 0) header("Range", "bytes=$downloaded-") }
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful || response.code == 206) { "HTTP ${response.code}" }
                val body = response.body ?: throw IOException("empty body")
                val isPartial = response.code == 206
                val contentLength = body.contentLength()
                // 服务器不支持 Range（返回 200 全量）时必须从头重写，否则文件前段重复损坏
                if (!isPartial && downloaded > 0) {
                    LogUtil.d(TAG, "server ignored Range, restart from 0")
                    downloaded = 0
                }
                if (contentLength > 0) {
                    val total = downloaded + contentLength
                    if (entity.length != total) {
                        dao.update(entity.copy(length = total, state = DownloadState.Downloading))
                    }
                }

                RandomAccessFile(file, "rwd").use { raf ->
                    raf.seek(downloaded)
                    raf.setLength(downloaded) // 截断到断点，清除可能的旧尾
                    val buffer = ByteArray(64 * 1024)
                    var lastFlush = 0L
                    body.byteStream().use { input ->
                        while (currentCoroutineContext().isActive) {
                            val n = input.read(buffer)
                            if (n == -1) break
                            raf.write(buffer, 0, n)
                            downloaded += n
                            val now = System.currentTimeMillis()
                            if (now - lastFlush >= 1_000) {
                                lastFlush = now
                                dao.update(entity.copy(downloadedLength = downloaded, state = DownloadState.Downloading))
                            }
                        }
                    }
                }
            }

            // 正常读完流 = 完成；length 未知（chunked）时用实际字节数回填，否则 UI 进度除零
            val fresh = dao.find(entity.videoCode, entity.quality)
            if (fresh != null) {
                val finalLength = maxOf(fresh.length, downloaded)
                dao.update(fresh.copy(length = finalLength, downloadedLength = finalLength, state = DownloadState.Finished))
                LogUtil.d(TAG, "finished: ${entity.title} (${finalLength} bytes)")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 暂停/删除：pauseTask 已把状态落库，这里只保证不误标失败
            dao.find(entity.videoCode, entity.quality)?.let {
                if (it.state == DownloadState.Downloading) dao.update(it.copy(state = DownloadState.Paused))
            }
            throw e
        } catch (e: Exception) {
            LogUtil.e(TAG, "download failed: ${entity.title}", e)
            dao.find(entity.videoCode, entity.quality)?.let {
                dao.update(it.copy(state = DownloadState.Failed))
            }
        } finally {
            jobs.remove(taskKey(entity0))
            activeCount.value = jobs.size
        }
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .readTimeout(java.time.Duration.ofSeconds(30))
            .build()
    }
}

/** M6-2：平台壳入口的公开薄封装（[downloadWorkController] 是 internal）。 */
fun initializeDesktopDownloadQueue() {
    runBlocking { DesktopDownloadWorkController.initialize() }
}

/** M6-2：跨模块薄封装（[downloadWorkController] 是 internal；供 :desktopApp 冒烟/壳层用）。 */
fun desktopDownloadWorkController(): DownloadWorkController = DesktopDownloadWorkController