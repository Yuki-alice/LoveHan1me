package lovehan1me.feature.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import lovehan1me.Res
import lovehan1me.cancel
import lovehan1me.confirm
import lovehan1me.crop_avatar
import lovehan1me.core.platform.AvatarCropRect
import lovehan1me.core.platform.cropAndSaveAvatar
import lovehan1me.core.platform.decodeAvatarSource
import lovehan1me.core.util.LogUtil
import lovehan1me.ui.component.appbar.HanimeScaffold
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max

private const val TAG = "AvatarCrop"
private const val MAX_ZOOM = 5f

/**
 * 阶段一⑧：头像裁剪（纯 Compose，三端共用）。
 *
 * 原来只有 Android 能用——`:app` 的实现依赖 `cn.mucute:compose-avatar-cropper`
 * （只有 `-android` 产物），桌面/iOS 落到 `NavPlaceholder`。
 *
 * 现在：缩放/平移/裁剪框这些**交互**全在共享层，**平台只做像素活**
 * （[decodeAvatarSource] 解码 + [cropAndSaveAvatar] 裁剪落盘）。
 * Android 仍可由 `PlatformScreens.avatarCrop` 注入 mucute 版本；不注入就用本实现。
 */
@Composable
fun AvatarCropScreen(
    sourceUri: String,
    onBack: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // 手势状态提到本层：确认按钮需要它来算源图坐标下的裁剪矩形
    val transform = remember { CropTransform() }
    var viewport by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(sourceUri) {
        val decoded = decodeAvatarSource(sourceUri)
        if (decoded == null) {
            LogUtil.w(TAG, "无法解码源图：$sourceUri")
            failed = true
        } else {
            bitmap = decoded
        }
    }

    HanimeScaffold(
        title = stringResource(Res.string.crop_avatar),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = bitmap
                when {
                    failed -> Text(
                        "无法读取该图片（平台暂不支持，或文件已失效）",
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    bmp == null -> CircularProgressIndicator()

                    else -> CropCanvas(
                        bitmap = bmp,
                        transform = transform,
                        onViewportChanged = {
                            viewport = it
                            transform.viewport = it
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onBack) {
                    Text(stringResource(Res.string.cancel))
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                Button(
                    enabled = bitmap != null && !saving,
                    onClick = {
                        val bmp = bitmap ?: return@Button
                        saving = true
                        scope.launch {
                            val path = cropAndSaveAvatar(
                                source = sourceUri,
                                rect = computeCropRect(bmp, viewport, transform),
                            )
                            saving = false
                            if (path != null) {
                                onConfirm(path)
                            } else {
                                LogUtil.w(TAG, "裁剪落盘失败")
                                failed = true
                            }
                        }
                    },
                ) {
                    Text(if (saving) "…" else stringResource(Res.string.confirm))
                }
            }
        }
    }
}

/**
 * 裁剪画布：图片居中适配（[ContentScale.Fit]），支持缩放/拖动；
 * 裁剪区恒为**正方形视口**，圆形遮罩只是形状提示。
 */
@Composable
private fun CropCanvas(
    bitmap: ImageBitmap,
    transform: CropTransform,
    onViewportChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .onSizeChanged { onViewportChanged(max(it.width, it.height).toFloat()) }
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(bitmap) {
                detectTransformGestures { _, pan, zoom, _ ->
                    transform.applyGesture(zoom, pan, bitmap)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = transform.scale
                    scaleY = transform.scale
                    translationX = transform.offset.x
                    translationY = transform.offset.y
                },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.06f)),
        )
    }
}

/** 缩放倍数与位移（相对"居中适配"的基准）。 */
private class CropTransform {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var viewport by mutableFloatStateOf(0f)

    fun applyGesture(zoom: Float, pan: Offset, bitmap: ImageBitmap) {
        scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
        val (dx, dy) = AvatarCropMath.clampOffset(
            bitmap.width, bitmap.height, viewport, scale, offset.x + pan.x, offset.y + pan.y,
        )
        offset = Offset(dx, dy)
    }
}

private fun computeCropRect(
    bitmap: ImageBitmap,
    viewport: Float,
    transform: CropTransform,
): AvatarCropRect {
    val r = AvatarCropMath.cropRect(
        srcW = bitmap.width,
        srcH = bitmap.height,
        viewport = viewport,
        scale = transform.scale,
        offsetX = transform.offset.x,
        offsetY = transform.offset.y,
    )
    return AvatarCropRect(r.x, r.y, r.size)
}
