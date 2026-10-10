package lovehan1me.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import lovehan1me.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 确认对话框组件。
 *
 * 基于 Material3 AlertDialog 封装，控制显示隐藏和双按钮回调。
 *
 * @param visible 是否显示对话框
 * @param title 标题文本
 * @param message 内容文本
 * @param confirmText 确认按钮文本
 * @param dismissText 取消按钮文本
 * @param onConfirm 确认回调
 * @param onDismiss 取消回调
 * @sample ConfirmDialogPreview
 */
@Composable
fun ConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmText: String,
    dismissText: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelable: Boolean = true,
    onDismissButtonClick: () -> Unit = onDismiss,
    icon: ImageVector? = null,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = { if (cancelable) onDismiss() },
        icon = icon?.let { { Icon(imageVector = it, contentDescription = null) } },
        // M3E 规范：Dialog 标题必须强调。走 headlineSmallEmphasized（= AppEmphasis.dialogTitle）。
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            dismissText?.let {
                TextButton(onClick = onDismissButtonClick) {
                    Text(it)
                }
            }
        },
    )
}

