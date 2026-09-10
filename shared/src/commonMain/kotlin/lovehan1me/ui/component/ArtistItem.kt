package lovehan1me.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.Res
import lovehan1me.h_chan_load_failed_small
import lovehan1me.h_chan_loading_small
import lovehan1me.core.domain.model.SubscriptionItem
import lovehan1me.ui.preview.fakeArtists
import lovehan1me.ui.screen.RetryableImage

/**
 * 艺术家条目组件
 *
 * @param artist 艺术家订阅信息，包含名称和头像地址
 * @param onClickArtist 点击回调，参数为艺术家名称
 * @param onLongClickArtist 长按回调，参数为艺术家名称
 * @param modifier 应用于根 [Column] 布局的修饰符，默认 [Modifier]
 *
 * @see SubscriptionItem
 * @see RetryableImage
 */
@Composable
fun ArtistItem(
    artist: SubscriptionItem,
    onClickArtist: (String) -> Unit,
    onLongClickArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHapticFeedback()
    Column(
        modifier = modifier
            .width(72.dp)
            .combinedClickable(
                onClick = {
                    haptic()
                    onClickArtist(artist.artistName)
                },
                onLongClick = {
                    haptic()
                    onLongClickArtist(artist.artistName)
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RetryableImage(
            model = artist.avatar,
            contentDescription = artist.artistName,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .border(4.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape),
            placeholder = painterResource(Res.drawable.h_chan_loading_small),
            error = painterResource(Res.drawable.h_chan_load_failed_small),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = artist.artistName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
