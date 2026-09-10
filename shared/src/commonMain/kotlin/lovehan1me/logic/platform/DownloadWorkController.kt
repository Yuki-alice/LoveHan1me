package lovehan1me.logic.platform

import lovehan1me.logic.entity.download.HanimeDownloadEntity
import kotlinx.coroutines.flow.Flow

/**
 * M6：下载引擎契约（B 站式客户端体验）。
 *
 * 数据面（三端共享）：Room KMP 的 [lovehan1me.logic.dao.DownloadDatabase]
 * 是唯一事实源——任务/进度/分组全部落库，UI（Downloading/DownloadedScreen）直接观察 DB 流。
 * 控制面（本契约）：平台各自实现队列执行器——
 * - Android：WorkManager（HanimeDownloadManager 转发，进程死亡可恢复）
 * - 桌面/iOS：P7 起为协程队列（当前 NoOp，浏览/删除已下载功能不受影响）
 */
interface DownloadWorkController {
    fun prune()
    suspend fun initialize()
    fun runningCount(): Flow<Int>

    /** P6d-4E：备份恢复后同步下载并发数上限 */
    fun updateDownloadLimit(count: Int) {}

    /** M6：暂停任务（进行中的标记回数据库后停止执行） */
    fun pauseTask(entity: HanimeDownloadEntity) {}

    /** M6：恢复任务 */
    fun resumeTask(entity: HanimeDownloadEntity) {}

    /** M6：删除任务（含已下产物） */
    fun deleteTask(entity: HanimeDownloadEntity) {}

    /** M6：删除某视频的产物文件夹（删除已下载记录时调用） */
    fun deleteVideoFolder(videoCode: String) {}

    /**
     * M6-2：发起下载（视频页「下载」按钮）。[video] 携带各清晰度直链
     * （[lovehan1me.logic.model.HanimeVideo.videoUrls]）。
     */
    suspend fun addTask(
        video: lovehan1me.logic.model.HanimeVideo,
        videoCode: String,
        quality: String?,
        groupId: Int,
        redownload: Boolean = false,
    ) {}

    /** M6：从下载目录导入已有视频（Android=SAF 扫描；桌面/iOS 返回 false） */
    suspend fun importDownloaded(): Boolean = false
}
