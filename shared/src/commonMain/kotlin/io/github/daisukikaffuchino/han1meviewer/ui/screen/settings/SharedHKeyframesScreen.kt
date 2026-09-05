package io.github.daisukikaffuchino.han1meviewer.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.daisukikaffuchino.han1meviewer.Res
import io.github.daisukikaffuchino.han1meviewer.here_is_empty
import io.github.daisukikaffuchino.han1meviewer.h_keyframe_title_prefix
import io.github.daisukikaffuchino.han1meviewer.logic.entity.HKeyframeEntity
import io.github.daisukikaffuchino.han1meviewer.logic.entity.HKeyframeHeader
import io.github.daisukikaffuchino.han1meviewer.logic.entity.HKeyframeType
import io.github.daisukikaffuchino.han1meviewer.ui.component.content.EmptyContent
import io.github.daisukikaffuchino.han1meviewer.ui.component.lazy.LazyColumn
import io.github.daisukikaffuchino.han1meviewer.ui.theme.HanimeDefaults
import io.github.daisukikaffuchino.han1meviewer.ui.component.rememberHapticFeedback
import io.github.daisukikaffuchino.han1meviewer.ui.player.formatPlaybackTime

@Composable
fun SharedHKeyframesScreen(
    items: List<HKeyframeType>,
    onOpenVideo: (String) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyContent(hint = stringResource(Res.string.here_is_empty))
        return
    }

    LazyColumn(
        enableItemAnimation = false,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items) { item ->
            when (item) {
                is HKeyframeHeader -> SharedHeader(item)
                is HKeyframeEntity -> SharedEntityCard(
                    item,
                    onOpenVideo = { onOpenVideo(item.videoCode) })
            }
        }
    }
}

@Composable
private fun SharedHeader(header: HKeyframeHeader) {
    Text(
        text = header.title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SharedEntityCard(
    entity: HKeyframeEntity,
    onOpenVideo: () -> Unit,
) {
    val haptic = rememberHapticFeedback()
    Card(
        shape = HanimeDefaults.Corners.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .clickable {
                    haptic()
                    onOpenVideo()
                }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                entity.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(Res.string.h_keyframe_title_prefix) + entity.videoCode,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "@${entity.author}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entity.keyframes.forEach { keyframe ->
                HorizontalDivider()
                Text(
                    text = formatPlaybackTime(keyframe.position),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                keyframe.prompt?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = "➥ $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
