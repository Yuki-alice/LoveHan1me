package me.lovehan1me.logic.platform

import me.lovehan1me.utils.LogUtil
import me.lovehan1me.USER_AGENT
import me.lovehan1me.logic.SettingsRepository
import me.lovehan1me.logic.dao.Han1meDatabases
import me.lovehan1me.logic.entity.download.DownloadGroupEntity
import me.lovehan1me.logic.entity.download.HanimeDownloadEntity
import me.lovehan1me.logic.model.HanimeVideo
import me.lovehan1me.logic.network.BridgeCookiesStorage
import me.lovehan1me.logic.state.DownloadState
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fseek
import platform.posix.ftruncate
import platform.posix.fileno
import platform.posix.fwrite
import kotlin.time.Clock

/**
 * M8-1a：iOS 下载引擎（B 站式体验的核心执行器）。
 *
 * 1:1 移植 [DesktopDownloadWorkController]（队列结构/分组播种/Range 续传/进度落库语义相同），
 * 平台差异仅三处：
 * - 网络栈：OkHttp → Ktor Darwin（`Range: bytes=n-` 语义相同；Cookie 经 BridgeCookiesStorage，
 *   与 iOS 主网络栈一致，站点防盗链/Referer 头照抄桌面）；
 * - 文件 IO：java.io.RandomAccessFile → posix fopen/fseek/ftruncate/fwrite
 *  （"r+b"+seek+截断 = 桌面 "rwd"+seek+setLength 的等价断点续传）；
 * - 并发容器/线程：ConcurrentHashMap → Mutex 守卫的 MutableMap，
 *   Dispatchers.IO → Dispatchers.Default（Native 无 IO 调度器）。
 *
 * 落盘目录：`Documents/Han1meViewer/downloads/<videoCode>/<title> [<quality>].<suffix>`
 * （与桌面 `<dir>/<videoCode>/…` 同构；根固定 Documents 以便文件 App 导出，
 * 不跟随 safDownloadPath——iOS 无 SAF 且跨端备份可能带入 Android 路径）。
 */
@OptIn(ExperimentalForeignApi::class)
object IosDownloadWorkController : DownloadWorkController {

    private const val TAG = "IosDownload"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 桌面用 ConcurrentHashMap；Native 无并发 Map，以 Mutex 串行化（见 [withJobs]）。 */
    private val jobsMutex = Mutex()
    private val jobs = mutableMapOf<String, Job>()

    private suspend inline fun <T> withJobs(block: MutableMap<String, Job>.() -> T): T =
        jobsMutex.withLock { jobs.block() }

    @Volatile
    private var maxConcurrent = 2

    @Volatile
    private var semaphore = Semaphore(maxConcurrent)

    /** 引擎活性（设置页/调试用；NoOp 平台恒 0） */
    private val activeCount = MutableStateFlow(0)

    override fun prune() {
        // iOS 无 WorkManager 尾账，no-op（对齐桌面）
    }

    override suspend fun initialize() {
        // 全新库 download_groups 为空会导致外键拒绝：先播种再恢复（对齐桌面的播种顺序）
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
        // Semaphore 无法扩容：仅对新任务的许可语义生效（下次重启重建，对齐桌面）
    }

    // ── 发起下载（视频页「下载」按钮 → 选清晰度）──

    override suspend fun addTask(
        video: HanimeVideo,
        videoCode: String,
        quality: String?,
        groupId: Int,
        redownload: Boolean,
    ) = withContext(Dispatchers.Default) {
        val q = quality ?: video.videoUrls.keys.firstOrNull() ?: return@withContext
        val link = video.videoUrls[q]?.link ?: return@withContext
        val suffix = video.videoUrls[q]?.suffix ?: "mp4"
        val db = Han1meDatabases.download
        val dao = db.hanimeDownloadDao

        // 全新库 download_groups 为空会导致外键拒绝（对齐桌面）
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
            addDate = Clock.System.now().toEpochMilliseconds(),
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
        // Map 操作进 scope 串行化（Native 无 ConcurrentHashMap）；取消后落库逻辑对齐桌面
        scope.launch {
            withJobs { remove(taskKey(entity)) }?.cancel()
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
        scope.launch {
            withJobs { remove(taskKey(entity)) }?.cancel()
            Han1meDatabases.download.hanimeDownloadDao.delete(entity.videoCode, entity.quality)
            deletePath(videoFilePath(entity))
        }
    }

    override fun deleteVideoFolder(videoCode: String) {
        deletePath(videoFolderPath(videoCode))
    }

    override suspend fun importDownloaded(): Boolean = false

    // ── 内部：执行器 ──

    private fun taskKey(entity: HanimeDownloadEntity) = "${entity.videoCode}|${entity.quality}"

    private fun documentsDir(): String =
        (NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true,
        ).first() as String)

    private fun downloadDir(): String = "${documentsDir()}/Han1meViewer/downloads"

    private fun videoFolderPath(videoCode: String) = "${downloadDir()}/$videoCode"

    private fun sanitizeFileName(name: String) =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120).ifBlank { "video" }

    private fun videoFilePath(entity: HanimeDownloadEntity): String =
        "${videoFolderPath(entity.videoCode)}/${sanitizeFileName(entity.title)} [${entity.quality}].${entity.suffix}"

    private fun ensureDir(path: String) {
        NSFileManager.defaultManager.createDirectoryAtPath(path, true, null, null)
    }

    private fun deletePath(path: String) {
        val fm = NSFileManager.defaultManager
        if (fm.fileExistsAtPath(path)) {
            // 目录递归删除（等价 File.deleteRecursively）
            fm.removeItemAtPath(path, null)
        }
    }

    /**
     * suspend 以保证注册同步（对齐桌面 `jobs[key] = scope.launch{}` 的同步语义：
     * 调用方 addTask/initialize/resume 均为 suspend 上下文；若改为异步注册，
     * 紧随的 pauseTask 会因查不到 job 而漏取消）。
     */
    private suspend fun startJob(entity: HanimeDownloadEntity) {
        val key = taskKey(entity)
        withJobs {
            if (containsKey(key)) return
            put(key, scope.launch {
                semaphore.withPermit {
                    runDownload(entity)
                }
            })
        }
    }

    private suspend fun runDownload(entity0: HanimeDownloadEntity) {
        val dao = Han1meDatabases.download.hanimeDownloadDao
        val entity = dao.find(entity0.videoCode, entity0.quality) ?: return
        val filePath = videoFilePath(entity)
        ensureDir(videoFolderPath(entity.videoCode))

        dao.update(entity.copy(state = DownloadState.Downloading))
        activeCount.value = withJobs { size }

        var downloaded = entity.downloadedLength
        if (downloaded < 0 || (entity.length in 1 until downloaded)) downloaded = 0

        try {
            httpClient.prepareGet {
                url(entity.videoUrl)
                header("User-Agent", USER_AGENT)
                header("Referer", SettingsRepository.baseUrl)
                if (downloaded > 0) header("Range", "bytes=$downloaded-")
            }.execute { response ->
                val code = response.status.value
                check(response.status.isSuccess() || code == 206) { "HTTP $code" }
                val isPartial = code == 206
                val contentLength = response.contentLength()
                // 服务器不支持 Range（返回 200 全量）时必须从头重写，否则文件前段重复损坏
                if (!isPartial && downloaded > 0) {
                    LogUtil.d(TAG, "server ignored Range, restart from 0")
                    downloaded = 0
                }
                if (contentLength != null && contentLength > 0) {
                    val total = downloaded + contentLength
                    if (entity.length != total) {
                        dao.update(entity.copy(length = total, state = DownloadState.Downloading))
                    }
                }

                // 等价 RandomAccessFile("rwd")+seek+setLength：续传位 seek 并截断旧尾
                val fp = if (downloaded > 0) {
                    fopen(filePath, "r+b")?.also {
                        fseek(it, downloaded, SEEK_SET)
                        ftruncate(fileno(it), downloaded)
                    }
                } else {
                    null
                } ?: fopen(filePath, "wb")?.also { downloaded = 0 }
                ?: throw IllegalStateException("cannot open file: $filePath")
                try {
                    val channel = response.bodyAsChannel()
                    val buffer = ByteArray(64 * 1024)
                    var lastFlush = 0L
                    while (currentCoroutineContext().isActive) {
                        val n = channel.readAvailable(buffer, 0, buffer.size)
                        if (n == -1) break
                        buffer.usePinned { pinned ->
                            fwrite(pinned.addressOf(0), 1UL, n.toULong(), fp)
                        }
                        downloaded += n
                        val now = Clock.System.now().toEpochMilliseconds()
                        if (now - lastFlush >= 1_000) {
                            lastFlush = now
                            fflush(fp)
                            dao.update(entity.copy(downloadedLength = downloaded, state = DownloadState.Downloading))
                        }
                    }
                    fflush(fp)
                } finally {
                    fclose(fp)
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
            withJobs { remove(taskKey(entity0)) }
            activeCount.value = withJobs { size }
        }
    }

    /**
     * 下载专用 Darwin 客户端（不对齐主网络栈的 15s 整请求超时——流式下载必然超限；
     * 超时语义对齐桌面 OkHttp：connect 15s / 读空闲 30s；Cookie 照抄主栈 Bridge）。
     */
    private val httpClient: HttpClient by lazy {
        HttpClient(Darwin) {
            install(HttpCookies) {
                storage = BridgeCookiesStorage()
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
        }
    }
}

/** M8-1a：跨模块薄封装（[downloadWorkController] 是 internal；供冒烟/壳层用，对齐桌面）。 */
fun iosDownloadWorkController(): DownloadWorkController = IosDownloadWorkController
