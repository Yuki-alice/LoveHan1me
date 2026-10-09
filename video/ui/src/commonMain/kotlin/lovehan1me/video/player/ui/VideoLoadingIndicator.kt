/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ContainedLoadingIndicator
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
            // P1 #11：缓冲浮层是"叠在内容上"的加载语义（over other content）——
            // 用 contained 档：38dp 容器给指示器一个稳定对比底座，亮画面帧上不再糊掉。
            // 规格依据：M3 loading indicator —— 容器用于叠在内容上的场景 / 下拉刷新。
            ContainedLoadingIndicator()
        }

        Row(Modifier.padding(top = 8.dp)) {
            ProvideTextStyleContentColor(textStyle, color = MaterialTheme.colorScheme.onSurface) {
                text()
            }
        }
    }
}
