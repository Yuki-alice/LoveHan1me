package lovehan1me.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import lovehan1me.ui.component.HapticTextButton as TextButton
import androidx.compose.runtime.Composable

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
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = { if (cancelable) onDismiss() },
        title = { Text(text = title) },
        text = { Text(text = message) },
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

