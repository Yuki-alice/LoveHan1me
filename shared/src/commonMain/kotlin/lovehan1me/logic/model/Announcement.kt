package lovehan1me.logic.model

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import lovehan1me.LOCAL_DATE_TIME_FORMAT
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

data class Announcement(
    val title: String,
    val content: String,
    val positiveText: String ? = null,
    val negativeText: String ? = null,
    val timestamp: Long = 0,
    val priority: Int = 1,
    val imageUrl: String ? = null,
    val isActive: Boolean = false
) {
    // Required by serializers and callers that need an empty model instance.
    constructor() : this("", "", null, null, 0L, 1, null, false)
    @OptIn(ExperimentalTime::class)
    fun getFormattedDate(): String {
        return kotlin.time.Instant
            .fromEpochSeconds(timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .format(LOCAL_DATE_TIME_FORMAT)
    }

    @Composable
    fun getFormatedContent(linkColor: Color = MaterialTheme.colorScheme.primary): AnnotatedString {
        val regex = "(https?://[\\w-]+(\\.[\\w-]+)+([/?%&=]*)?)".toRegex()

        return buildAnnotatedString {
            val matches = regex.findAll(content).toList()

            if (matches.isEmpty()) {
                append(content)
            } else {
                var lastIdx = 0
                for (match in matches) {
                    append(content.substring(lastIdx, match.range.first))
                    withLink(
                        LinkAnnotation.Url(
                            url = match.value,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        )
                    ) {
                        append(match.value)
                    }
                    lastIdx = match.range.last + 1
                }
                if (lastIdx < content.length) {
                    append(content.substring(lastIdx))
                }
            }
        }
    }
}
