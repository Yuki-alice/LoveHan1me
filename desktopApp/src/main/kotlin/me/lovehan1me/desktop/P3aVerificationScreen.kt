package me.lovehan1me.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import me.lovehan1me.logic.SettingsRepository
import me.lovehan1me.logic.Parser
import me.lovehan1me.logic.state.WebsiteState
import me.lovehan1me.logic.network.HanimeNetwork
import me.lovehan1me.utils.LogUtil
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

/**
 * P3a 验证屏（临时脚手架，desktopApp 内）：
 * DataStore 已由 Main 初始化；这里做 真实 HTTP → HTML 解析(ksoup) → Coil 图片 的整链路验证。
 * 状态行：HTTP ${code} · N items · ${耗时}ms；刷新按钮可重复请求；错误不吞、全文展示。
 */
private data class P3aItem(
    val title: String,
    val coverUrl: String?,
    val code: String?,
)

private val videoCodeRegex = Regex("watch\\?v=(\\d+)")

@Composable
fun P3aVerificationScreen() {
    MaterialTheme {
        var refreshKey by remember { mutableIntStateOf(0) }
        var loading by remember { mutableStateOf(false) }
        var items by remember { mutableStateOf(emptyList<P3aItem>()) }
        var statusLine by remember { mutableStateOf("尚未请求（点刷新）") }
        var errorText by remember { mutableStateOf<String?>(null) }
        // 无 GUI 交互的自动化运行里，第一次成功后自动触发一次二次请求（等价人工点刷新）
        var autoSecondDone by remember { mutableStateOf(false) }
        var lastStatusCode by remember { mutableStateOf<Int?>(null) }
        // 图片加载统计（onSuccess/onError 回调累加）
        var imgOk by remember { mutableIntStateOf(0) }
        var imgFail by remember { mutableIntStateOf(0) }
        var parserOutcome by remember { mutableStateOf("Parser: -") }

        // 沙箱无外网时可把首页指到本地 fixture（如 P3A_HOME_URL=http://127.0.0.1:18080/home.html）
        val homeUrl = System.getenv("P3A_HOME_URL")?.takeIf { it.isNotBlank() }
            ?: SettingsRepository.homeUrl

        LaunchedEffect(refreshKey) {
            loading = true
            errorText = null
            items = emptyList()
            imgOk = 0
            imgFail = 0
            LogUtil.d("P3a", "load begin #$refreshKey url=$homeUrl")
            val start = TimeSource.Monotonic.markNow()
            try {
                val resp = HanimeNetwork.hanimeService.getHomePage(homeUrl)
                LogUtil.d("P3a", "response arrived HTTP ${resp.status.value} in ${start.elapsedNow().inWholeMilliseconds}ms")
                lastStatusCode = resp.status.value
                if (resp.status.value != 200) {
                    val html = resp.bodyAsText()
                    LogUtil.w("P3a", "non-200 body head(500): ${html.take(500)}")
                    statusLine = "HTTP ${resp.status.value} · 非 200（见下方 body 前 500 字符）"
                    errorText = "HTTP ${resp.status.value}\n--- body head 500 ---\n${html.take(500)}"
                    return@LaunchedEffect
                }
                val html = resp.bodyAsText()
                LogUtil.d("P3a", "body received length=${html.length}")
                val parsed = withContext(Dispatchers.Default) { parseHomeItems(html) }
                items = parsed.first
                LogUtil.d("P3a", "parsed items=${parsed.first.size} note=${parsed.second}")
                statusLine = "HTTP ${resp.status.value} · ${parsed.first.size} items · ${start.elapsedNow().inWholeMilliseconds}ms · ${parsed.second}"
                // E：P4 迁移后首次真调 Parser.homePageVer2（suspend）
                val parserState = Parser.homePageVer2(html)
                parserOutcome = when (val ps = parserState) {
                    is WebsiteState.Success -> {
                        val hp = ps.info
                        val allRows = listOf(hp.latestHanime, hp.latestRelease, hp.ecchiAnime, hp.watchingNow, hp.newAnimeTrailer)
                            .flatten().distinctBy { it.title }
                        val samples = allRows.take(3).joinToString(" | ") { it.title }
                        "Parser Success: rows=${allRows.size} latestHanime=${hp.latestHanime.size} samples=[${samples}]"
                    }
                    is WebsiteState.Error ->
                        "Parser Error: ${ps.throwable::class.simpleName}: ${ps.throwable.message}"
                    WebsiteState.Loading -> "Parser Loading"
                }
                LogUtil.d("P3a", parserOutcome)            } catch (t: Throwable) {
                LogUtil.e("P3a", "load failed", t)
                errorText = buildString {
                    append(t::class.simpleName).append(": ").append(t.message ?: "(null message)")
                    append('\n')
                    append(t.stackTraceToString())
                }
                statusLine = "加载失败（见错误区）"
            } finally {
                loading = false
            }
            // 自动化替身：第一次(200)成功后 8 秒自动再来一次，验证二次请求/缓存/cookie 链路
            if (refreshKey == 0 && !autoSecondDone && lastStatusCode == 200) {
                autoSecondDone = true
                LogUtil.d("P3a", "auto second request in 8s (代替人工点刷新)")
                delay(8000)
                refreshKey++
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { refreshKey++ }, enabled = !loading) {
                    Text("刷新 #$refreshKey")
                }
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
                Text(
                    statusLine,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.weight(1f, fill = true),
                )
            }

            errorText?.let { err ->
                Text(
                    "⚠ 错误：\n$err",
                    color = Color(0xFFCC0000),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(Color(0xFFFFF3F3))
                        .verticalScroll(rememberScrollState())
                        .padding(6.dp),
                )
            }

            Text(
                "封面图：加载成功 $imgOk · 失败 $imgFail",
                style = MaterialTheme.typography.caption,
            )
            Text(
                parserOutcome,
                style = MaterialTheme.typography.caption,
                color = Color(0xFF000099),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))

            if (items.isEmpty() && !loading && errorText == null) {
                Text("无解析结果。")
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items) { item ->
                    P3aCard(
                        item = item,
                        onImageOk = { imgOk++ },
                        onImageFail = { imgFail++ },
                    )
                }
            }
        }
    }
}

@Composable
private fun P3aCard(
    item: P3aItem,
    onImageOk: () -> Unit,
    onImageFail: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFF5F5F5)).padding(8.dp)) {
        Box(
            modifier = Modifier
                .width(150.dp)
                .aspectRatio(16f / 9f)
                .background(Color(0xFFDDDDDD)),
        ) {
            val cover = item.coverUrl
            if (cover != null) {
                LaunchedEffect(cover) { LogUtil.d("P3a", "image start $cover") }
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { LogUtil.d("P3a", "image OK $cover"); onImageOk() },
                    onError = { s -> LogUtil.e("P3a", "image FAIL $cover", s.result.throwable); onImageFail() },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("no cover", modifier = Modifier.align(Alignment.Center))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.fillMaxHeight()) {
            Text(item.title, style = MaterialTheme.typography.body2)
            Spacer(Modifier.height(4.dp))
            Text(
                "code=${item.code ?: "-"}",
                style = MaterialTheme.typography.caption,
                color = Color(0xFF666666),
            )
            item.coverUrl?.let {
                Text(
                    "img=${it.take(70)}",
                    style = MaterialTheme.typography.caption,
                    color = Color(0xFF888888),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/**
 * 用 ksoup(DOM/select) 解析首页。返回 (条目, 说明)。
 * 选择器对照 :app Parser.kt（只读参照，未修改）：
 *  普通横卡 div[class^=horizontal-card]（title: div.title,h4.video-title；封面首个 img 的 data-src/src）
 *  兜底：div.home-rows-videos-div / a 简化卡。
 */
private fun parseHomeItems(html: String): Pair<List<P3aItem>, String> {
    val doc = Ksoup.parse(html)
    val out = LinkedHashMap<String, P3aItem>()

    val cards = doc.select("div[class^=horizontal-card]").asList()
    var normalCount = 0
    for (card in cards) {
        if (out.size >= 20) break
        normalCount++
        parseNormalCard(card)?.let { item ->
            out[item.code ?: (item.title + item.coverUrl)] = item
        }
    }

    var simplifiedCount = 0
    if (out.size < 12) {
        val rows = doc.select("div.home-rows-videos-div").asList()
        for (row in rows) {
            if (out.size >= 20) break
            simplifiedCount++
            parseSimplified(row)?.let { item ->
                out[item.code ?: (item.title + item.coverUrl)] = item
            }
        }
    }

    val note = "normal=$normalCount simplifiedScan=$simplifiedCount"
    return out.values.take(20).toList() to note
}

private fun parseNormalCard(card: Element): P3aItem? {
    val title = card.selectFirst("div.title, h4.video-title")?.text()?.trim()
        ?: card.attr("title").takeIf { it.isNotBlank() }
    val img = card.select("img").asList().firstOrNull()
    val coverRaw = img?.let { it.attr("data-src").ifBlank { it.attr("src") } }
        ?.takeIf { it.isNotBlank() }
    val href = card.select("a[href]").asList().firstOrNull()?.attr("href").orEmpty()
    val code = videoCodeRegex.find(href)?.groupValues?.get(1)
    return if (title == null && coverRaw == null) null
    else P3aItem(title ?: "(no title)", absolutize(coverRaw), code)
}

private fun parseSimplified(row: Element): P3aItem? {
    val a = row.selectFirst("a[href]") ?: row
    val title = (a.selectFirst("div.home-rows-videos-title, div[class$=title]")?.text()?.trim())
        ?: a.attr("title").takeIf { it.isNotBlank() }
    val img = a.select("img").asList().firstOrNull()
    val coverRaw = img?.let { it.attr("data-src").ifBlank { it.attr("src") } }
        ?.takeIf { it.isNotBlank() }
    val code = videoCodeRegex.find(a.attr("href"))?.groupValues?.get(1)
    return if (title == null && coverRaw == null) null
    else P3aItem(title ?: "(no title)", absolutize(coverRaw), code)
}

/** 把封面 URL 规整为绝对地址：//x → https:x；/x → origin+x；其余相对 → baseUrl + x */
private fun absolutize(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val s = raw.trim()
    val base = SettingsRepository.baseUrl
    return when {
        s.startsWith("http://") || s.startsWith("https://") -> s
        s.startsWith("//") -> "https:$s"
        s.startsWith("/") -> originOf(base) + s
        else -> base + s
    }
}

private fun originOf(base: String): String {
    val schemeEnd = base.indexOf("://")
    if (schemeEnd < 0) return base
    val pathStart = base.indexOf('/', schemeEnd + 3)
    return if (pathStart < 0) base else base.substring(0, pathStart)
}
