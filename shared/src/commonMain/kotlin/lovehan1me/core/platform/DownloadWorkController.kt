package lovehan1me.core.platform

import lovehan1me.data.database.entity.download.HanimeDownloadEntity
import kotlinx.coroutines.flow.Flow

/**
 * 下载引擎契约（B 站式客户端体验；桌面/iOS 拉齐见 Gate4）。
 *
 * 数据面（三端共享）：Room KMP 的 [lovehan1me.data.database.dao.DownloadDatabase]
 * 是唯一事实源——任务/进度/分组全部落库，UI（Downloading/DownloadedScreen）直接观察 DB 流。
 * 控制面（本契约）：平台各自实现队列执行器——
 * - Android：WorkManager（HanimeDownloadManager 转发，进程死亡可恢复）
 * - 桌面：`DesktopDownloadWorkController`（协程队列 + Range 续传）
 * - iOS：`IosDownloadWorkController`（1:1 移植桌面，网络栈换 Ktor Darwin、文件 IO 换 posix）
 *
 * Gate4：三端均为真实现，契约里的空默认体只作为「平台不支持该动作」的兜底，
 * 不再是"待实现"的占位。
 */
interface DownloadWorkController {
    fun prune()
    suspend fun initialize()
    fun runningCount(): Flow<Int>

    /** P6d-4E：备份恢复后同步下载并发数上限 */
    fun updateDownloadLimit(count: Int) {}

    /** 暂停任务（进行中的标记回数据库后停止执行） */
    fun pauseTask(entity: HanimeDownloadEntity) {}

    /** 恢复任务 */
    fun resumeTask(entity: HanimeDownloadEntity) {}

    /** 删除任务（含已下产物） */
    fun deleteTask(entity: HanimeDownloadEntity) {}

    /** 删除某视频的产物文件夹（删除已下载记录时调用） */
    fun deleteVideoFolder(videoCode: String) {}

    /**
     * 发起下载（视频页「下载」按钮）。[video] 携带各清晰度直链
     * （[lovehan1me.core.domain.model.HanimeVideo.videoUrls]）。
     */
    suspend fun addTask(
        video: lovehan1me.core.domain.model.HanimeVideo,
        videoCode: String,
        quality: String?,
        groupId: Int,
        redownload: Boolean = false,
    ) {}

    /** 从下载目录导入已有视频（Android=SAF 扫描；桌面/iOS=自家目录扫描） */
    suspend fun importDownloaded(): Boolean = false

    /**
     * 阶段一⑦：是否支持从系统文件选择器导入外部视频（当前仅 iOS）。
     *
     * iOS 沙盒外文件无稳定访问权，导入 = 经 DocumentPicker 拷贝进自家下载目录
     * 再走与 [importDownloaded] 同一套入库扫描；Android（SAF 常驻权限）/
     * 桌面（文件系统直读）不需要这条路。
     */
    fun supportsExternalImport(): Boolean = false

    /**
     * 阶段一⑦：导入单个外部文件。
     *
     * @param tempPath 选择器落到应用可读位置的临时路径（iOS 由
     * `rememberBackupImportLauncher` 回传，文件名即原文件名）。
     * @return true=已入库（调用方刷新列表），false=失败或非受支持格式。
     */
    suspend fun importExternalFile(tempPath: String): Boolean = false
}
