/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.gesture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import lovehan1me.video.player.ui.support.slightlyWeaken

/** 浮在画面角落的按钮底板（锁屏、截图那类），无边框只留一层极淡的底色。 */
@Composable
fun PlayerFloatingButtonBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background.copy(0.05f),
        contentColor = Color.White,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.slightlyWeaken()),
    ) {
        content()
    }
}
