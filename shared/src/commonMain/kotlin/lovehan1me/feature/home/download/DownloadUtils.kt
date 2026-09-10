package lovehan1me.feature.home.download

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import lovehan1me.Res
import lovehan1me.ungrouped
import lovehan1me.feature.player.paused
import lovehan1me.desktop.loading
import lovehan1me.download_progress_percent
import lovehan1me.download_failed_tap_retry
import lovehan1me.download_complete
import lovehan1me.already_in_queue
import lovehan1me.ic_check_circle
import lovehan1me.ic_download
import lovehan1me.ic_error_outline
import lovehan1me.ic_pause
import lovehan1me.ic_play_arrow
import lovehan1me.data.database.entity.download.DownloadGroupEntity
import lovehan1me.data.database.entity.download.VideoWithCategories
import lovehan1me.core.domain.model.DownloadHeaderNode
import lovehan1me.core.domain.model.DownloadItemNode
import lovehan1me.core.domain.model.DownloadedNode
import lovehan1me.core.domain.state.DownloadState
import org.jetbrains.compose.resources.DrawableResource

/**
 * 将已下载视频列表按分组 ID 转换为 [DownloadHeaderNode] 列表。
 *
 * @param groupIdToNameMap 分组 ID -> 名称映射
 * @param collapseDownloadedGroup 默认是否折叠分组
 * @return 按分组聚合后的 Header 节点列表
 */
fun List<VideoWithCategories>.toNodeList(
    groupIdToNameMap: Map<Int, String>,
    collapseDownloadedGroup: Boolean,
): List<DownloadHeaderNode> {
    // P6d-2：原 Map.toSortedMap() 在 commonMain 解析失败（同工具链 desktop 可用，
    // 疑似 commonizer 可见性 quirk，待人工复核）；等价改写为 entries 排序（迭代有序性一致）。
    val groupedData = this.groupBy { it.video.groupId }.toList().sortedBy { it.first }.toMap()
    return buildList {
        for ((groupId, videos) in groupedData) {
            add(
                DownloadHeaderNode(
                    groupKey = groupIdToNameMap[groupId] ?: "ID: $groupId",
                    originalVideos = videos,
                    isExpanded = !collapseDownloadedGroup,
                )
            )
        }
    }
}

/**
 * 将 Header 列表展开为扁平节点列表（Header + 展开状态下的子项）。
 *
 * @return 扁平化的 [DownloadedNode] 列表
 */
fun List<DownloadHeaderNode>.toFlatNodeList(): List<DownloadedNode> {
    val flatList = mutableListOf<DownloadedNode>()
    for (header in this) {
        flatList.add(header)
        if (header.isExpanded) {
            header.originalVideos.forEach { video ->
                flatList.add(DownloadItemNode(video, header.groupKey))
            }
        }
    }
    return flatList
}

/**
 * 将未分组的分组名称替换为"未分组"字符串资源。
 *
 * @param List<DownloadGroupEntity> 分组列表
 * @return 替换后的分组列表
 */
@Composable
fun List<DownloadGroupEntity>.toDisplayGroups(): List<DownloadGroupEntity> = map { group ->
    if (group.id == DownloadGroupEntity.DEFAULT_GROUP_ID) {
        group.copy(name = stringResource(Res.string.ungrouped))
    } else {
        group
    }
}

/**
 * 下载状态对应的显示文本。
 *
 * @param state 下载状态
 * @param progress 下载进度 (0-100)
 * @return 本地化文本
 */
@Composable
fun downloadStateText(state: DownloadState, progress: Int): String = when (state) {
    DownloadState.Queued -> stringResource(Res.string.already_in_queue)
    DownloadState.Downloading -> stringResource(Res.string.download_progress_percent, progress)
    DownloadState.Paused -> stringResource(Res.string.paused)
    DownloadState.Failed -> stringResource(Res.string.download_failed_tap_retry)
    DownloadState.Finished -> stringResource(Res.string.download_complete)
    DownloadState.Unknown -> stringResource(Res.string.loading)
}

/**
 * 下载状态对应的图标资源。
 *
 * @param state 下载状态
 * @return 图标 drawable 资源
 */
fun downloadStateIcon(state: DownloadState): DrawableResource = when (state) {
    DownloadState.Queued -> Res.drawable.ic_play_arrow
    DownloadState.Downloading -> Res.drawable.ic_pause
    DownloadState.Paused -> Res.drawable.ic_play_arrow
    DownloadState.Failed -> Res.drawable.ic_error_outline
    DownloadState.Finished -> Res.drawable.ic_check_circle
    DownloadState.Unknown -> Res.drawable.ic_download
}
