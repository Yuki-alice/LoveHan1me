package lovehan1me.core.domain.model

import androidx.compose.runtime.Immutable

/**
 * 卡片模型契约。标 `@Immutable`：三个实现（`HanimeInfo` / `SubscriptionVideosItem` /
 * `SitePlaylistCard`）全是 val，编译器据此把 `VideoCardItem` 判为可跳过 ——
 * 滚动时卡片不再逐帧重组。如新增可变实现，先改这里再动调用方。
 */
@Immutable
interface VideoItemType {
    val title: String
    val coverUrl: String
    val videoCode: String
    val duration: String?
    val views: String?
    val reviews: String?
    val currentArtist: String?
    val uploadTime: String?
}