/*
 * 本文件实现移植自 Animeko (https://github.com/open-ani/animeko)，
 * 受 GNU AGPLv3 许可证约束，详见仓库根目录 LICENSE 与 NOTICE。
 */

package lovehan1me.video.player.ui.support

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** 同时提供 [LocalTextStyle] 与 [LocalContentColor]。 */
@Composable
fun ProvideTextStyleContentColor(
    value: TextStyle,
    color: Color = LocalContentColor.current,
    content: @Composable () -> Unit,
) {
    val mergedStyle = LocalTextStyle.current.merge(value)
    CompositionLocalProvider(
        LocalTextStyle provides mergedStyle,
        LocalContentColor provides color,
        content = content,
    )
}

/**
 * 描边文字。播放器叠在画面上，底色随时会变，只靠颜色对比度看不清。
 */
@Composable
fun TextWithBorder(
    text: String,
    width: TextUnit = 14.sp,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
    borderDrawStyle: DrawStyle? = Stroke(
        width = with(LocalDensity.current) {
            width.toPx() / 15
        },
        miter = 3f,
        join = StrokeJoin.Round,
    ),
    borderColor: Color = Color.DarkGray,
) {
    Box {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            overflow = overflow,
            maxLines = maxLines,
            style = style.copy(
                color = borderColor,
                drawStyle = borderDrawStyle,
            ),
        )
        Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            overflow = overflow,
            maxLines = maxLines,
            style = style,
        )
    }
}
