package lovehan1me.feature.danmaku

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import lovehan1me.Res
import lovehan1me.close
import lovehan1me.danmaku_back_to_subjects
import lovehan1me.danmaku_episode_count_format
import lovehan1me.danmaku_no_result
import lovehan1me.danmaku_pick_episode
import lovehan1me.danmaku_picker_title
import lovehan1me.danmaku_search_action
import lovehan1me.danmaku_search_failed
import lovehan1me.danmaku_search_hint
import lovehan1me.danmaku_searching
import lovehan1me.danmaku_unlink
import lovehan1me.data.danmaku.DanmakuEpisodeRef
import lovehan1me.data.danmaku.DanmakuSubject

/**
 * 人工选集弹窗：**这才是弹幕的主路径**。
 *
 * 里番标题在弹弹play 库里基本命中不了（那是 Bangumi 体系），所以自动匹配只是"顺手猜一下"，
 * 真正的关联由这里完成：搜番 → 选一集 → 落库（manual=true，从此不再猜）。
 *
 * ## 异常必须露出来
 * 与自动匹配那条路相反，这里**不吞异常**：用户主动点了检索，"没搜到"与"搜不了"
 * 是两句话（前者显示空结果，后者带出原因）。把网络错误显示成"没有匹配结果"
 * 会让人反复改关键字，是最消耗人的一种误导。
 */
@Composable
fun DanmakuMatchPickerDialog(
    session: DanmakuSession,
    initialKeyword: String = "",
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var keyword by remember { mutableStateOf(initialKeyword) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var subjects by remember { mutableStateOf<List<DanmakuSubject>?>(null) }
    var subject by remember { mutableStateOf<DanmakuSubject?>(null) }
    var episodes by remember { mutableStateOf<List<DanmakuEpisodeRef>?>(null) }
    val status by session.status.collectAsStateWithLifecycle()

    /** 三步共用同一套兜底：协程取消照常抛出，其余失败变成一句人话。 */
    suspend fun attempt(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            error = getString(Res.string.danmaku_search_failed, failure.message.orEmpty())
        } finally {
            busy = false
        }
    }

    fun searchSubjects() {
        val query = keyword.trim()
        if (query.isEmpty()) return
        scope.launch {
            attempt {
                subject = null
                episodes = null
                subjects = session.searchSubjects(query)
            }
        }
    }

    LaunchedEffect(initialKeyword) {
        // 带着片名进来就直接搜一遍：用户十次有十次就是要找这部
        if (initialKeyword.isNotBlank()) searchSubjects()
    }

    val pickedSubject = subject
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (pickedSubject == null) Res.string.danmaku_picker_title
                    else Res.string.danmaku_pick_episode
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pickedSubject == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = keyword,
                            onValueChange = { keyword = it },
                            placeholder = { Text(stringResource(Res.string.danmaku_search_hint)) },
                            singleLine = true,
                            enabled = !busy,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = ::searchSubjects, enabled = !busy) {
                            Text(stringResource(Res.string.danmaku_search_action))
                        }
                    }
                } else {
                    // 选到番剧这一层：列表顶上说明"是哪部"，并给一条回去改关键字的路
                    Text(text = pickedSubject.title, style = MaterialTheme.typography.labelLarge)
                    TextButton(
                        onClick = {
                            subject = null
                            episodes = null
                            error = null
                        },
                    ) {
                        Text(stringResource(Res.string.danmaku_back_to_subjects))
                    }
                }

                when {
                    busy -> Text(stringResource(Res.string.danmaku_searching))

                    error != null -> Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                    )

                    pickedSubject != null -> {
                        val list = episodes.orEmpty()
                        ResultList(
                            titles = list.map { it.displayTitle() },
                            subtitle = { index -> list[index].subjectTitle },
                            onPick = { index ->
                                val episode = list[index]
                                scope.launch { attempt { session.link(episode); onDismiss() } }
                            },
                        )
                    }

                    else -> {
                        val list = subjects.orEmpty()
                        ResultList(
                            titles = list.map { it.title },
                            subtitle = { index ->
                                stringResource(
                                    Res.string.danmaku_episode_count_format,
                                    list[index].episodeCount,
                                )
                            },
                            onPick = { index ->
                                val picked = list[index]
                                scope.launch {
                                    attempt { episodes = session.searchEpisodes(picked.title) }
                                    subject = picked
                                }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            // 只有真关联上了才给「取消关联」，否则这个钮点了就是空动作
            if (status is DanmakuStatus.Linked) {
                TextButton(
                    onClick = {
                        val wasBusy = busy
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                session.unlink()
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (failure: Throwable) {
                                error = getString(
                                    Res.string.danmaku_search_failed,
                                    failure.message.orEmpty(),
                                )
                            } finally {
                                busy = wasBusy
                            }
                        }
                    },
                ) {
                    Text(stringResource(Res.string.danmaku_unlink))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.close))
            }
        },
    )
}

@Composable
private fun ResultList(
    titles: List<String>,
    subtitle: @Composable (Int) -> String,
    onPick: (Int) -> Unit,
) {
    if (titles.isEmpty()) {
        Text(stringResource(Res.string.danmaku_no_result))
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        titles.forEachIndexed { index, title ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .clickable { onPick(index) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                val sub = subtitle(index)
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
