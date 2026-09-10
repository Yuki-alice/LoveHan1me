package me.lovehan1me.ui.screen.settings

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
import me.lovehan1me.Res
import me.lovehan1me.here_is_empty
import me.lovehan1me.h_keyframe_title_prefix
import me.lovehan1me.logic.entity.HKeyframeEntity
import me.lovehan1me.logic.entity.HKeyframeHeader
import me.lovehan1me.logic.entity.HKeyframeType
import me.lovehan1me.ui.component.content.EmptyContent
import me.lovehan1me.ui.component.lazy.LazyColumn
import me.lovehan1me.ui.theme.HanimeDefaults
import me.lovehan1me.ui.component.rememberHapticFeedback
import me.lovehan1me.ui.player.formatPlaybackTime

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
