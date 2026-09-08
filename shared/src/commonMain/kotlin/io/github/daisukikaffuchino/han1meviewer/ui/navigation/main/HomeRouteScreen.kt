package io.github.daisukikaffuchino.han1meviewer.ui.navigation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.daisukikaffuchino.han1meviewer.ui.viewmodel.sharedViewModel
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.cancel
import io.github.daisukikaffuchino.han1meviewer.checkout_exit
import io.github.daisukikaffuchino.han1meviewer.confirm_exit_message
import io.github.daisukikaffuchino.han1meviewer.confirm_to_exit
import io.github.daisukikaffuchino.han1meviewer.copy_to_clipboard
import io.github.daisukikaffuchino.han1meviewer.do_more
import io.github.daisukikaffuchino.han1meviewer.exit
import io.github.daisukikaffuchino.han1meviewer.finished_masturbating
import io.github.daisukikaffuchino.han1meviewer.getHanimeShareText
import io.github.daisukikaffuchino.han1meviewer.logic.DatabaseRepo
import io.github.daisukikaffuchino.han1meviewer.logic.SettingsRepository
import io.github.daisukikaffuchino.han1meviewer.logic.entity.CheckInType
import io.github.daisukikaffuchino.han1meviewer.logic.model.Announcement
import io.github.daisukikaffuchino.han1meviewer.ui.component.ConfirmDialog
import io.github.daisukikaffuchino.han1meviewer.ui.component.TripleButtonDialog
import io.github.daisukikaffuchino.han1meviewer.ui.screen.home.dailycheckin.formatHm
import io.github.daisukikaffuchino.han1meviewer.ui.screen.home.dailycheckin.today
import io.github.daisukikaffuchino.han1meviewer.ui.screen.home.homepage.HomeUiEvent
import io.github.daisukikaffuchino.han1meviewer.ui.screen.home.homepage.HomePageViewModel
import io.github.daisukikaffuchino.han1meviewer.ui.screen.home.homepage.LocalSearchHistoryQuery
import io.github.daisukikaffuchino.han1meviewer.ui.screen.home.homepage.SharedHomeScreen
import io.github.daisukikaffuchino.han1meviewer.ui.screen.home.homepage.component.AnnouncementDialog
import io.github.daisukikaffuchino.han1meviewer.ui.viewmodel.CheckInCalendarViewModel
import io.github.daisukikaffuchino.han1meviewer.update_link_open_failed
import io.github.daisukikaffuchino.utils.SonnerToast
import io.github.daisukikaffuchino.utils.rememberCopyTextToClipboard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * M1：三端共享的首页路由装配（对标 `:app` 的 `HomeRouteScreen`，去掉 `MainActivity` 依赖）。
 *
 * 与 `:app` 版的差异：
 * - `activity.viewModel` 改 `viewModel()`（调用方若需跨页面共享 VM，可自行提升 owner，M2 导航装配时统一）；
 * - `activity.finish()` 改 [onExit] 回调（桌面=关闭窗口，iOS=no-op，Android=finish）；
 * - 其余事件处理（剪贴板/更新链接/公告/退出确认/打卡）与 `:app` 逐行对齐。
 */
@Composable
fun SharedHomeRouteScreen(
    onOpenDrawer: () -> Unit,
    onNavigateToPreview: () -> Unit,
    onNavigateToSearch: (String?) -> Unit,
    onNavigateToSearchAdvanced: (Map<String, String>) -> Unit,
    onNavigateToVideo: (String) -> Unit,
    onExit: () -> Unit,
    // M2：调用方（App）可提升 VM，以便登录成功后刷新首页；默认与独立调用一致
    //（同 owner 下 sharedViewModel 同 key 命中同一实例）。
    viewModel: HomePageViewModel = sharedViewModel(::HomePageViewModel),
    showNavigationIcon: Boolean = false,
) {
    // 裸 viewModel() 在桌面/iOS 必崩（默认 SavedStateViewModelFactory(nonAndroid)
    // 调 Factory.create(String, CreationExtras) 抛 UnsupportedOperationException，
    // M1 桌面冒烟实测），统一走 sharedViewModel helper。
    val checkInEnabled by SettingsRepository.checkInEnabledFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val checkInViewModel: CheckInCalendarViewModel? =
        if (checkInEnabled) {
            sharedViewModel(::CheckInCalendarViewModel)
        } else null
    val copyTextToClipboard = rememberCopyTextToClipboard()
    val uriHandler = LocalUriHandler.current
    val confirmToExit = stringResource(Res.string.confirm_to_exit)
    val confirmExitMessage = stringResource(Res.string.confirm_exit_message)
    val cancel = stringResource(Res.string.cancel)
    val exit = stringResource(Res.string.exit)
    var showExitDialog by remember { mutableStateOf(false) }
    var announcement by remember { mutableStateOf<Announcement?>(null) }
    CompositionLocalProvider(
        LocalSearchHistoryQuery provides { keyword: String ->
            DatabaseRepo.SearchHistory.loadAll(keyword).first().map { it.query }
        }
    ) {
        SharedHomeScreen(
            viewModel = viewModel,
            showNavigationIcon = showNavigationIcon,
            onOpenDrawer = onOpenDrawer,
            onEvent = { event ->
                when (event) {
                    is HomeUiEvent.OpenDrawer -> onOpenDrawer()
                    is HomeUiEvent.NavigateToPreview -> onNavigateToPreview()
                    is HomeUiEvent.OpenSearchPage -> onNavigateToSearch(event.query)
                    is HomeUiEvent.NavigateToSearchAdvanced -> onNavigateToSearchAdvanced(event.params)
                    is HomeUiEvent.OpenVideo -> onNavigateToVideo(event.videoCode)
                    is HomeUiEvent.LongPressVideoCopy -> {
                        copyTextToClipboard(getHanimeShareText(event.videoTitle, event.videoCode))
                        scope.launch { SonnerToast.success(getString(Res.string.copy_to_clipboard)) }
                    }
                    is HomeUiEvent.ShowAnnouncementDialog -> { announcement = event.announcement }
                    is HomeUiEvent.ShowExitDialog -> { showExitDialog = true }
                    is HomeUiEvent.OpenUpdatePage -> {
                        runCatching { uriHandler.openUri(event.downloadUrl) }
                            .onFailure { scope.launch { SonnerToast.error(getString(Res.string.update_link_open_failed)) } }
                    }
                    is HomeUiEvent.IgnoreUpdate -> viewModel.ignoreUpdate(event.versionCode)
                }
            }
        )
    }

    if (showExitDialog && checkInEnabled) {
        TripleButtonDialog(
            visible = true,
            title = confirmToExit,
            message = stringResource(Res.string.finished_masturbating),
            negativeText = stringResource(Res.string.do_more),
            neutralText = stringResource(Res.string.checkout_exit),
            positiveText = exit,
            onNegative = { showExitDialog = false },
            onNeutral = {
                checkInViewModel?.addRecord(
                    today(),
                    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time.formatHm(),
                    CheckInType.MASTURBATION.storeName,
                    "",
                )
                onExit()
            },
            onPositive = { onExit() },
            onDismiss = { showExitDialog = false },
        )
    } else if (showExitDialog) {
        ConfirmDialog(
            visible = true,
            title = confirmToExit,
            message = confirmExitMessage,
            confirmText = exit,
            dismissText = cancel,
            onConfirm = { onExit() },
            onDismiss = { showExitDialog = false },
        )
    }

    announcement?.let { data ->
        AnnouncementDialog(
            announcementData = data,
            onDismiss = { announcement = null },
        )
    }
}
