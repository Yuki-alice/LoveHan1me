package lovehan1me.feature.home.homepage.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import lovehan1me.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import lovehan1me.ui.component.HanimeAsyncImage
import lovehan1me.ui.component.LinkifiedText
import coil3.request.ImageRequest
import coil3.request.crossfade
import lovehan1me.Res
import lovehan1me.saved
import lovehan1me.sure
import lovehan1me.save_image_confirm
import lovehan1me.i_understand
import lovehan1me.cancel
import lovehan1me.ic_alert
import lovehan1me.core.domain.model.Announcement
import lovehan1me.ui.component.rememberHapticFeedback
import lovehan1me.ui.component.ConfirmDialog
import lovehan1me.feature.home.homepage.saveImageToGallery
import lovehan1me.core.platform.ioDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 单条公告的详情弹窗。
 *
 * 正文里的裸链接由 [LinkifiedText] 渲染成可点链接 —— 该逻辑原先挂在
 * `Announcement` 模型上的 `@Composable getFormatedContent()`，让领域模型反向依赖 Compose。
 *
 * [onDismiss] 在**任何一个按钮**上都会触发：两个按钮（negativeText / positiveText）
 * 目前都只表示"我看过了"，没有第二种语义。真要有"去处理"这类动作，
 * 得先给 `Announcement` 加"动作"字段，而不是让同一个回调承担两件事。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnnouncementDialog(
    announcementData: Announcement,
    onDismiss: () -> Unit,
) {
    val context = LocalPlatformContext.current
    val haptic = rememberHapticFeedback()
    val scope = rememberCoroutineScope()
    var showFullScreenImage by remember { mutableStateOf(false) }
    var showSaveImageConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_alert),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.height(16.dp))

                    if (announcementData.title.isNotBlank()) {
                        Text(
                            text = announcementData.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    if (announcementData.timestamp > 0) {
                        Text(
                            text = announcementData.getFormattedDate(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    LinkifiedText(
                        text = announcementData.content,
                        style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )

                    if (!announcementData.imageUrl.isNullOrBlank()) {
                        Spacer(Modifier.height(16.dp))
                        HanimeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(announcementData.imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .combinedClickable(
                                    onClick = {
                                        haptic()
                                        showFullScreenImage = true
                                    },
                                    onLongClick = {
                                        haptic()
                                        showSaveImageConfirm = true
                                    },
                                ),
                        )
                    }
                }

                if (!announcementData.negativeText.isNullOrBlank() &&
                    !announcementData.positiveText.isNullOrBlank()
                ) {
                    Spacer(Modifier.height(24.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Announcement 下沉 shared 后跨模块不可 smart cast，先绑局部
                    val dialogNegative = announcementData.negativeText
                    val dialogPositive = announcementData.positiveText
                    if (!dialogNegative.isNullOrBlank()) {
                        TextButton(onClick = onDismiss) {
                            Text(text = dialogNegative)
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    TextButton(onClick = onDismiss) {
                        Text(
                            text = dialogPositive
                                ?: stringResource(Res.string.i_understand)
                        )
                    }
                }
            }
        }
    }

    if (showFullScreenImage && !announcementData.imageUrl.isNullOrBlank()) {
        Dialog(
            onDismissRequest = { showFullScreenImage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            HanimeAsyncImage(
                model = announcementData.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        onClick = {
                            haptic()
                            showFullScreenImage = false
                        },
                        onLongClick = {
                            haptic()
                            showSaveImageConfirm = true
                        },
                    ),
            )
        }
    }

    if (showSaveImageConfirm && !announcementData.imageUrl.isNullOrBlank()) {
        // 跨模块属性不可 smart cast，orEmpty 转非空
        val imageUrl = announcementData.imageUrl.orEmpty()
        ConfirmDialog(
            visible = true,
            title = stringResource(Res.string.save_image_confirm),
            message = "",
            confirmText = stringResource(Res.string.sure),
            dismissText = stringResource(Res.string.cancel),
            onConfirm = {
                showSaveImageConfirm = false
                scope.launch(ioDispatcher) {
                    val ok = saveImageToGallery(imageUrl)
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        if (ok) {
                            lovehan1me.core.util.AppToast.success(
                                getString(Res.string.saved)
                            )
                        }
                    }
                }
            },
            onDismiss = { showSaveImageConfirm = false },
        )
    }
}
