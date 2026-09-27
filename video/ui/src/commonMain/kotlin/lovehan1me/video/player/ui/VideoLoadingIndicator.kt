/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import lovehan1me.video.player.ui.support.ProvideTextStyleContentColor

@Composable
fun VideoLoadingIndicator(
    showProgress: Boolean,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (showProgress) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        }

        Row(Modifier.padding(top = 8.dp)) {
            ProvideTextStyleContentColor(textStyle, color = MaterialTheme.colorScheme.onSurface) {
                text()
            }
        }
    }
}
