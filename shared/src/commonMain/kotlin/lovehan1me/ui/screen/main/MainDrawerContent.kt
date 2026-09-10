package lovehan1me.ui.screen.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lovehan1me.Res
import lovehan1me.h_chan_default_avatar
import lovehan1me.ic_switch
import lovehan1me.loading
import lovehan1me.my_list
import lovehan1me.not_logged_in
import lovehan1me.refresh_page_or_login_expired
import lovehan1me.switch_site
import lovehan1me.video
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.ui.navigation.main.MainDrawerDestination
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.StringResource

/**
 * 富信息抽屉头（自 `:app` 下沉，包名不变）。
 *
 * 下沉替换（行为一致）：
 * - `VibrationUtil.performHapticFeedback(view)` → 共享 [rememberHapticFeedback]
 *  （桌面/iOS 为空实现，见各端 actual）；
 * - coil `AsyncImage` → 共享 [HanimeAsyncImage]（同签名）；
 * - `@Preview` 留在 `:app` 侧（commonMain 无 preview 基建）。
 */
@Composable
fun MainDrawerHeader(
    avatarUrl: String?,
    username: String?,
    isLoggedIn: Boolean,
    isLoading: Boolean,
    currentSite: String,
    onAvatarClick: () -> Unit,
    onAvatarLongClick: (() -> Unit)? = null,
    onSwitchSiteClick: (() -> Unit)? = null,
) {
    val haptic = rememberHapticFeedback()
    val cardShape = RoundedCornerShape(28.dp)
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardPressed = cardInteractionSource.collectIsPressedAsState().value
    val cardScale = animateFloatAsState(
        targetValue = if (isCardPressed) 0.98f else 1f,
        label = "drawerHeaderCardScale"
    ).value
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp)
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp)
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                }
                .clip(cardShape)
                .combinedClickable(
                    interactionSource = cardInteractionSource,
                    indication = ripple(),
                    onClick = {
                        haptic()
                        onAvatarClick()
                    },
                ),
            shape = cardShape,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HanimeAsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = {
                                haptic()
                                onAvatarClick()
                            },
                            onLongClick = onAvatarLongClick?.let { longClick ->
                                {
                                    haptic()
                                    longClick()
                                }
                            },
                        ),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(Res.drawable.h_chan_default_avatar),
                    fallback = painterResource(Res.drawable.h_chan_default_avatar),
                    error = painterResource(Res.drawable.h_chan_default_avatar),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp, end = 8.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    Text(
                        text = when {
                            isLoading -> stringResource(Res.string.loading)
                            isLoggedIn -> username
                                ?: stringResource(Res.string.refresh_page_or_login_expired)

                            else -> stringResource(Res.string.not_logged_in)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentSite,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 站点切换是 Android 专属能力（activity 弹窗）；null 时整列隐藏，
                // 桌面/iOS 传 null（此前最小抽屉本就没有该入口）。
                if (onSwitchSiteClick != null) {
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Min)
                            .align(Alignment.CenterVertically)
                            .padding(top = 6.dp)
                            .clickable(
                                onClick = {
                                    haptic()
                                    onSwitchSiteClick()
                                },
                                indication = ripple(bounded = false),
                                interactionSource = remember { MutableInteractionSource() }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_switch),
                                contentDescription = stringResource(Res.string.switch_site)
                            )
                        }

                        Text(
                            text = stringResource(Res.string.switch_site),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.alpha(0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * 完整抽屉内容（头 + 目的地分组，自 `:app MainActivityScaffold.kt` 下沉，包名不变）。
 *
 * 平台钩子可空语义：`onAvatarLongClick`（登出）/`onSwitchSiteClick`（切站）为
 * Android 专属，桌面/iOS 传 null（长按无动作/切站列隐藏）；其余行为三端一致。
 */
@Composable
fun MainDrawerContent(
    selectedDestination: MainDrawerDestination?,
    avatarUrl: String?,
    username: String?,
    isLoggedIn: Boolean,
    isLoading: Boolean,
    currentSite: String,
    checkInEnabled: Boolean,
    onAvatarClick: () -> Unit,
    onAvatarLongClick: (() -> Unit)? = null,
    onSwitchSiteClick: (() -> Unit)? = null,
    onDrawerItemSelected: (MainDrawerDestination) -> Boolean,
) {
    MainDrawerHeader(
        avatarUrl = avatarUrl,
        username = username,
        isLoggedIn = isLoggedIn,
        isLoading = isLoading,
        currentSite = currentSite,
        onAvatarClick = onAvatarClick,
        onAvatarLongClick = onAvatarLongClick,
        onSwitchSiteClick = onSwitchSiteClick,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        MainDrawerPrimaryItems(
            selectedDestination = selectedDestination,
            onDrawerItemSelected = onDrawerItemSelected,
            checkInEnabled = checkInEnabled,
        )
        MainDrawerSection(
            titleRes = Res.string.my_list,
            items = listOf(
                MainDrawerDestination.WatchLater,
                MainDrawerDestination.FavVideo,
                MainDrawerDestination.Playlist,
                MainDrawerDestination.Subscription,
            ),
            selectedDestination = selectedDestination,
            onItemClick = { onDrawerItemSelected(it) },
        )
        MainDrawerSection(
            titleRes = Res.string.video,
            items = listOf(
                MainDrawerDestination.WatchHistory,
                MainDrawerDestination.Download,
            ),
            selectedDestination = selectedDestination,
            onItemClick = { onDrawerItemSelected(it) },
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MainDrawerPrimaryItems(
    selectedDestination: MainDrawerDestination?,
    onDrawerItemSelected: (MainDrawerDestination) -> Boolean,
    checkInEnabled: Boolean,
) {
    val haptic = rememberHapticFeedback()
    val primaryItems = buildList {
        add(MainDrawerDestination.Home)
        add(MainDrawerDestination.Settings)
        if (checkInEnabled) add(MainDrawerDestination.DailyCheckIn)
    }
    Column {
        primaryItems.forEach { item ->
            NavigationDrawerItem(
                label = { Text(stringResource(item.titleRes)) },
                icon = {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = stringResource(item.titleRes),
                    )
                },
                selected = selectedDestination == item,
                onClick = {
                    haptic()
                    onDrawerItemSelected(item)
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
    }
}

@Composable
private fun MainDrawerSection(
    titleRes: StringResource,
    items: List<MainDrawerDestination>,
    selectedDestination: MainDrawerDestination?,
    onItemClick: (MainDrawerDestination) -> Unit,
) {
    val haptic = rememberHapticFeedback()
    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 12.dp),
    )
    Column {
        items.forEach { item ->
            NavigationDrawerItem(
                label = { Text(stringResource(item.titleRes)) },
                icon = {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = stringResource(item.titleRes),
                    )
                },
                selected = selectedDestination == item,
                onClick = {
                    haptic()
                    onItemClick(item)
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
    }
}
