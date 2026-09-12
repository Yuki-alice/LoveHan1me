package lovehan1me.feature.home.homepage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import lovehan1me.core.domain.model.AppUpdateInfo
import lovehan1me.core.domain.model.Announcement
import lovehan1me.ui.component.lazy.LazyColumn
import lovehan1me.feature.preview.fakeAnnouncements
import lovehan1me.feature.preview.fakeHomePage
import lovehan1me.feature.home.homepage.component.AnnouncementCard
import lovehan1me.feature.home.homepage.component.AppUpdateCard
import lovehan1me.feature.home.homepage.component.BannerCarousel
import lovehan1me.feature.home.homepage.component.CategoryBlock
import lovehan1me.ui.adaptive.rememberPageHorizontalMargin

/**
 * 渲染首页可滚动内容区域。
 *
 * @param data 主页数据
 * @param onEvent 主页事件回调
 * @param onCloseAnnouncement 关闭公告时调用。
 * @param modifier 应用于列表根布局的修饰符。
 */
@Composable
fun HomePageContent(
    data: HomeData,
    updateInfo: AppUpdateInfo?,
    updateAnnouncement: Announcement?,
    isAVSite: Boolean,
    onEvent: (HomeUiEvent) -> Unit,
    onCloseAnnouncement: () -> Unit,
    contentTopPadding: Dp,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val banners = remember(data.page.banner) {
        listOfNotNull(data.page.banner)
    }
    val announcements = remember(data.announcements) {
        data.announcements.filter { it.isActive }
    }

    val categories = remember(data.page, isAVSite) {
        buildCategoryList(data.page, isAVSite)
    }
    // P4：页面左右边距统一由窗口档驱动，各区块不再各写各的 12.dp，
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
        if (updateAnnouncement != null) {
            item(key = "update_announcement") {
                AnnouncementCard(
                    announcements = listOf(updateAnnouncement),
                    onAnnouncementClick = { announcement ->
                        onEvent(HomeUiEvent.ShowAnnouncementDialog(announcement))
                    },
                    onClose = null,
                    modifier = Modifier.padding(horizontal = margin, vertical = 4.dp),
                )
            }
        }
        if (announcements.isNotEmpty()) {
            item(key = "announcement") {
                AnnouncementCard(
                    announcements = announcements,
                    onAnnouncementClick = { announcement ->
                        onEvent(HomeUiEvent.ShowAnnouncementDialog(announcement))
                    },
                    onClose = onCloseAnnouncement,
                    modifier = Modifier.padding(horizontal = margin, vertical = 4.dp)
                )
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
}
