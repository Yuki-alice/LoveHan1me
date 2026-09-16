package lovehan1me.feature.home.homepage.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import lovehan1me.Res
import lovehan1me.ic_calendar_month
import lovehan1me.ic_person
import lovehan1me.ic_search
import lovehan1me.my_account
import lovehan1me.new_anime_list
import lovehan1me.ui.adaptive.rememberPageHorizontalMargin
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.ui.theme.HanimeDefaults

/** 顶栏内容区高度（不含状态栏 inset）。 */
private val HomeTopBarHeight = 64.dp
/** 顶栏三件套之间的间距。 */
private val HomeTopBarSpacing = 10.dp
/** 胶囊高度（搜索框与新番列表同高）。 */
private val PillHeight = 40.dp
/** 账号头像直径。 */
private val AvatarSize = 34.dp

/**
 * 首页顶栏「三件套」（P4）：**搜索框 · 新番列表 · 账号头像**。
 *
 * 取代原先的「标题 + 刷新/搜索/预览三个图标按钮」：
 * - 标题没有信息量（用户永远知道自己在首页），换成常驻搜索框更省一次点击；
 * - 搜索框是**跳转入口不是就地输入**：点击后进发现页并聚焦，既保留"一按就能搜"的
 *   即时感，又不会在首页叠一层输入态（首页还要下拉刷新，输入态会打架）。
 *
 * **相对设计稿 §1.3 的一处有意偏离**：原型在 Medium+ 画的是「标题 + 搜索图标 + 头像」，
 * 这里全档统一用三件套，搜索框用 `weight(1f)` 吃掉剩余宽度。理由是不想为同一个顶栏
 * 维护两套形态，而搜索框在宽屏同样可用（顶栏从左到右填满，也不会出现右侧大片留白）。
 * 若后续要加标题，在 [HomeTopBar] 的 Row 里插一个 `Text` 即可。
 *
 * 色值一律走语义色（[HanimeDefaults.Colors] / colorScheme），**不照搬原型里的
 * `#F0E7EA` 之类硬编码** —— 原型定的是布局与断点行为，视觉以项目现有 M3 Expressive 为准。
 */
@Composable
fun HomeTopBar(
    onSearchClick: () -> Unit,
    onNewAnimeListClick: () -> Unit,
    onAvatarClick: () -> Unit,
    avatarUrl: String?,
    isLoggedIn: Boolean,
    modifier: Modifier = Modifier,
) {
    val margin = rememberPageHorizontalMargin()
    // 顶栏自身**不垫**状态栏：唯一调用点 SharedHomeScreen 坐在 MainScaffold 的
    // Scaffold 内容槽里，innerPadding 已经吃过一遍状态栏；这里再垫就是双倍 inset
    // （iPhone 上方会多出一整块状态栏高度的空白）。要在无 Scaffold 处复用时再另行处理。
    Column(modifier = modifier.background(HanimeDefaults.Colors.pageSurface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HomeTopBarHeight)
                .padding(horizontal = margin),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HomeTopBarSpacing),
        ) {
            SearchPill(onClick = onSearchClick, modifier = Modifier.weight(1f))
            NewAnimeListPill(onClick = onNewAnimeListClick)
            AccountAvatar(
                avatarUrl = avatarUrl,
                isLoggedIn = isLoggedIn,
                onClick = onAvatarClick,
            )
        }
    }
}

@Composable
private fun SearchPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 纯入口：只留搜索图标，不放提示文案（情感化文案等资产齐了再系统性设计）。
    Row(
        modifier = modifier
            .height(PillHeight)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = HanimeDefaults.Spacing.large),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HanimeDefaults.Spacing.medium),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_search),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NewAnimeListPill(onClick: () -> Unit) {
    // 去圆底：纯图标按钮，点缀色收敛到图标 tint —— 与中性底拉开层级，
    // 但不再是一坨色块。触控目标走 M3 IconButton 默认（48dp 最小可点）。
    val label = stringResource(Res.string.new_anime_list)
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(Res.drawable.ic_calendar_month),
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun AccountAvatar(
    avatarUrl: String?,
    isLoggedIn: Boolean,
    onClick: () -> Unit,
) {
    // 无障碍标签不能为 null：iOS XCUITest 与屏幕阅读器都靠它定位该按钮。
    val label = stringResource(Res.string.my_account)
    val personPainter = painterResource(Res.drawable.ic_person)
    if (isLoggedIn && avatarUrl != null) {
        HanimeAsyncImage(
            model = avatarUrl,
            contentDescription = label,
            modifier = Modifier
                .size(AvatarSize)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick),
            contentScale = ContentScale.Crop,
            placeholder = personPainter,
            error = personPainter,
            fallback = personPainter,
        )
    } else {
        // 未登录：纯图标按钮，无圆底（与新番入口同理）。
        IconButton(onClick = onClick) {
            Icon(
                painter = personPainter,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
