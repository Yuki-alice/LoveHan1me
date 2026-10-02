package lovehan1me.feature.home.homepage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.view_all
import lovehan1me.core.domain.model.AppUpdateInfo
import lovehan1me.core.domain.model.Announcement
import lovehan1me.core.domain.model.AnnouncementSeverity
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.feature.home.homepage.component.AnnouncementCard
import lovehan1me.feature.home.homepage.component.AnnouncementListDialog
import lovehan1me.feature.home.homepage.component.AppUpdateCard
import lovehan1me.feature.home.homepage.component.BannerCarousel
import lovehan1me.feature.home.homepage.component.CategoryBlock
import lovehan1me.ui.adaptive.rememberPageHorizontalMargin

/**
 * 渲染首页可滚动内容区域。
 *
 * @param data 主页数据
 * @param updateInfo 可用的应用更新（无则 null）
 * @param announcements 该展示的公告（已由 `AnnouncementRepository` 去重/剔过期/剔已读/排序）
 * @param onEvent 主页事件回调
 * @param onAnnouncementRead 某条公告被用户看过（关闭其详情）时调用，调用方落盘已读
 * @param modifier 应用于列表根布局的修饰符。
 */
@Composable
fun HomePageContent(
    data: HomeData,
    updateInfo: AppUpdateInfo?,
    announcements: List<Announcement>,
    isAVSite: Boolean,
    onEvent: (HomeUiEvent) -> Unit,
    onAnnouncementRead: (Announcement) -> Unit,
    contentTopPadding: Dp,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    var showAnnouncementList by remember { mutableStateOf(false) }

    val banners = remember(data.page.banner) {
        listOfNotNull(data.page.banner)
    }

    // 卡片位只放常规级，且只放最靠前的一条：阻断级由 SharedHomeScreen 的独占页承担，
    // 提示级按定义不占首页。其余的都从「查看全部」进列表弹窗。
    val cardAnnouncement = announcements
        .firstOrNull { it.severity == AnnouncementSeverity.Normal }
    val hasMoreAnnouncements = announcements.size > 1

    val categories = remember(data.page, isAVSite) {
        buildCategoryList(data.page, isAVSite)
    }
    // 页面左右边距统一由窗口档驱动，各区块不再各写各的 12.dp，
    // 否则分类矩阵（按扣边距后的宽度分档）会比 banner 宽出一截、视觉对不齐。
    val margin = rememberPageHorizontalMargin()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = contentTopPadding),
    ) {
        item(key = "banner") {
            BannerCarousel(
                banners = banners,
                onBannerClick = { videoCode ->
                    videoCode?.let {
                        onEvent(HomeUiEvent.OpenVideo(it))
                    }
                },
                modifier = Modifier.padding(horizontal = margin, vertical = 6.dp)
            )
        }
        if (updateInfo != null) {
            item(key = "app_update_${updateInfo.versionCode}") {
                AppUpdateCard(
                    updateInfo = updateInfo,
                    onUpdateClick = {
                        onEvent(HomeUiEvent.OpenUpdatePage(updateInfo.downloadUrl))
                    },
                    onIgnoreClick = {
                        onEvent(HomeUiEvent.IgnoreUpdate(updateInfo.versionCode))
                    },
                    modifier = Modifier.padding(horizontal = margin, vertical = 6.dp),
                )
            }
        }
        if (cardAnnouncement != null) {
            item(key = "announcement_${cardAnnouncement.stableKey}") {
                Column(
                    modifier = Modifier.padding(horizontal = margin, vertical = 4.dp)
                ) {
                    AnnouncementCard(
                        announcement = cardAnnouncement,
                        onAnnouncementClick = { announcement ->
                            onEvent(HomeUiEvent.ShowAnnouncementDialog(announcement))
                        },
                        onClose = { onAnnouncementRead(cardAnnouncement) },
                    )
                    if (hasMoreAnnouncements) {
                        TextButton(
                            onClick = { showAnnouncementList = true },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(
                                text = stringResource(Res.string.view_all),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
        categories.forEach { category ->
            item(key = "category_${category.titleRes}") {
                CategoryBlock(
                    title = stringResource(category.titleRes),
                    videos = category.videos,
                    onMoreClick = {
                        val params = category.toAdvancedSearchParams()
                        if (params.isNotEmpty()) {
                            onEvent(HomeUiEvent.NavigateToSearchAdvanced(params))
                        }
                    },
                    onVideoClick = { code ->
                        onEvent(HomeUiEvent.OpenVideo(code))
                    },
                    onVideoLongClick = { _, _ ->
                       // onEvent(HomeUiEvent.LongPressVideoCopy(code, title))
                    },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }

    if (showAnnouncementList) {
        AnnouncementListDialog(
            announcements = announcements,
            onDismiss = { showAnnouncementList = false },
            onAnnouncementRead = onAnnouncementRead,
        )
    }
}
