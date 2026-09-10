package lovehan1me.ui.screen.home.preview.getchupreview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import lovehan1me.Res
import lovehan1me.getchu_story
import lovehan1me.getchu_staff
import lovehan1me.getchu_product_intro
import lovehan1me.brand
import lovehan1me.h_chan_load_failed
import lovehan1me.h_chan_loading
import lovehan1me.core.domain.model.GetchuPreview
import lovehan1me.core.domain.model.GetchuPreviewDetail
import lovehan1me.ui.component.CardContainerSurface
import lovehan1me.ui.component.lazy.LazyRow

@Composable
internal fun GetchuPreviewItemCard(
    item: GetchuPreview.Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader,
) {
    CardContainerSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = getchuImageRequest(item.coverUrl),
                imageLoader = imageLoader,
                contentDescription = item.title,
                modifier = Modifier
                    .size(width = 108.dp, height = 148.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(Res.drawable.h_chan_loading),
                error = painterResource(Res.drawable.h_chan_load_failed)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                item.brand?.let {
                    Text(
                        "${stringResource(Res.string.brand)}: $it",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                item.price?.let {
                    Text(
                        "$it (JPY)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
internal fun GetchuTextSection(section: GetchuPreviewDetail.TextSection) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GetchuSectionTitle(getchuTextSectionTitle(section.title))
        CardContainerSurface(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SelectionContainer {
                    Text(
                        section.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun getchuTextSectionTitle(title: String): String {
    return when {
        title.contains("商品紹介") -> stringResource(Res.string.getchu_product_intro)
        title.contains("ストーリー") -> stringResource(Res.string.getchu_story)
        title.contains("スタッフ") -> stringResource(Res.string.getchu_staff)
        else -> title
    }
}

@Composable
internal fun GetchuSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
internal fun GetchuRelatedRow(
    title: String,
    items: List<GetchuPreview.Item>,
    onNavigateToDetail: (String) -> Unit,
    imageLoader: ImageLoader,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GetchuSectionTitle(title)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { item ->
                GetchuPreviewItemCard(
                    item = item,
                    onClick = { onNavigateToDetail(item.id) },
                    modifier = Modifier.width(260.dp),
                    imageLoader = imageLoader
                )
            }
        }
    }
}
