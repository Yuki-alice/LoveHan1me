package lovehan1me.feature.danmaku

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import lovehan1me.data.danmaku.DanmakuEpisodeRef
import lovehan1me.data.danmaku.DanmakuItem
import lovehan1me.data.danmaku.DanmakuLoadResult
import lovehan1me.data.danmaku.DanmakuLocation
import lovehan1me.data.danmaku.DanmakuProvider
import lovehan1me.data.danmaku.DanmakuSubject
import lovehan1me.ui.preview.HanimePreviewTheme
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [DanmakuLayer] 的离屏渲染：把一整批弹幕按真实播放器区域（1280×720）落笔成 PNG。
 *
 * 跑法：`:shared:desktopTest --tests "lovehan1me.feature.danmaku.DanmakuLayerRenderTest" --offline`
 * 产物：`shared/build/danmaku-renders/` 下的 3 张 PNG（日志打印绝对路径）。
 *
 * ## 为什么值得单独渲一次
 * 引擎的布局算式在 commonTest 的 `DanmakuEngineTest` 里已经逐条锁死，
 * 但那些断言看不见**像素**：描边有没有把字糊住、行高会不会让两行贴在一起、
 * 顶部/底部固定弹幕有没有跑出画面 —— 只有出图能查。
 * 因此这里不重复断言坐标，只断言一件事：**画面上确实落了字**。
 *
 * ## 时钟同源
 * [DanmakuLayer] 的播放位置来自 `withFrameNanos`，而 `ImageComposeScene.render(nanos)`
 * 正是那个帧时钟的驱动源。所以这里是**逐帧推进**（每帧 50ms，并同步喂一次位置采样），
 * 而不是"锚定一次然后跳到目标时刻"。三张图分别取自位置 0.6s / 1.5s / 2.6s 之后再稳几帧
 * —— 稳帧是必需的（见 [renderScene]），对断言没有影响：这一批弹幕全在 2s 内入场，
 * 20Hz 之下几帧内不会走完一条。
 */
class DanmakuLayerRenderTest {

    private val episode = DanmakuEpisodeRef(episodeId = "42", episodeTitle = "第 1 话")

    /** 绘制层不需要真数据源：仓库动作全部注入成假函数，这里只有一个空壳。 */
    private class StubProvider : DanmakuProvider {
        override val id: String = "stub"
        override suspend fun autoMatch(rawTitle: String): DanmakuEpisodeRef? = null
        override suspend fun searchSubjects(keyword: String): List<DanmakuSubject> = emptyList()
        override suspend fun searchEpisodes(subjectTitle: String): List<DanmakuEpisodeRef> =
            emptyList()

        override suspend fun fetch(episodeId: String): List<DanmakuItem> = emptyList()
    }

    private fun item(
        id: Int,
        timeMs: Long,
        text: String,
        color: Int = 0xFFFFFFFF.toInt(),
        location: DanmakuLocation = DanmakuLocation.SCROLL,
    ) = DanmakuItem(
        id = id.toLong(),
        playTimeMillis = timeMs,
        text = text,
        color = color,
        location = location,
    )

    /**
     * 一批覆盖全部形态的弹幕：不同长度（换行/碰撞堆叠）、三种颜色、
     * 滚动 + 顶部固定 + 底部固定。必须**按时间升序**（引擎的发射扫描要求有序）。
     */
    private fun scriptItems(): List<DanmakuItem> = listOf(
        item(1, 0L, "开场第一条：白色滚动弹幕"),
        item(2, 100L, "黄色高亮", 0xFFFFFF00.toInt()),
        item(3, 200L, "这一条特别长，用来验证同轨碰撞时会不会把下一行挤到第二条轨道上去，" +
            "顺便看看描边在长文本上是否依旧清晰"),
        item(4, 300L, "青色", 0xFF00E5FF.toInt()),
        item(5, 400L, "短句1"),
        item(6, 500L, "短句2"),
        item(7, 600L, "顶部固定弹幕", location = DanmakuLocation.TOP),
        item(8, 700L, "底部固定弹幕", location = DanmakuLocation.BOTTOM),
        item(9, 900L, "后到的滚动弹幕"),
        item(10, 1_100L, "再一条", 0xFFFFFF00.toInt()),
        item(11, 1_400L, "密集段落 A"),
        item(12, 1_450L, "密集段落 B"),
        item(13, 1_500L, "密集段落 C"),
        item(14, 2_000L, "临近出图时刻的一条"),
    )

    private fun newSession(scope: CoroutineScope) = DanmakuSession(
        videoCode = "render-test",
        title = "渲染回归用",
        provider = StubProvider(),
        scope = scope,
        resolve = { _, _, _ -> DanmakuLoadResult.Ready(episode, scriptItems()) },
        linkTo = { _, _, _ -> DanmakuLoadResult.Ready(episode, scriptItems()) },
        unlinkFrom = {},
    )

    /**
     * 按 [FRAME_STEP_MS] 一帧帧推到 `atPositionMs + SETTLE_FRAMES`，在最后一帧出图。
     *
     * 为什么不能"锚定一帧、再渲一帧"了事：`DanmakuLayer` 的播放位置来自
     * `withFrameNanos` 的帧时刻，而那个时刻**只有 `render()` 会推进**；同时它写进
     * state 的值要下一帧才被 `Canvas` 看到。两帧之下时钟几乎没走，位置停在 0，
     * 滚动弹幕还全在画面右沿外（`x = viewport.width`），于是截到一张空图。
     *
     * 为什么每帧要**真等** [FRAME_STEP_MS]（见 [tickScene]）：空屏时帧循环里那个
     * `delay(50)` 挂在 `Dispatchers.Unconfined` 上，用的是墙上时钟，不跟 `render()`
     * 的纳秒走。推进度快过真实速度时，绘制层每醒一次面对的是跳了几百毫秒的位置，
     * 引擎按 `lateGraceMs=150` 把它们全当迟到丢掉 —— 图上就什么都没有了。
     */
    private fun renderScene(name: String, session: DanmakuSession, atPositionMs: Long): File {
        val scene = ImageComposeScene(
            width = 1_280,
            height = 720,
            density = Density(2f),
            content = {
                HanimePreviewTheme(modifier = Modifier.fillMaxSize()) {
                    // 深灰底：纯黑看不出白字，纯白看不出描边
                    Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray)) {
                        DanmakuLayer(session = session, modifier = Modifier.fillMaxSize())
                    }
                }
            },
        )
        try {
            var elapsedMs = 0L
            while (elapsedMs < atPositionMs + SETTLE_FRAMES * FRAME_STEP_MS) {
                elapsedMs += FRAME_STEP_MS
                tickScene(scene, session, elapsedMs).close()
            }
            val image = tickScene(scene, session, elapsedMs + FRAME_STEP_MS)
            val out = File(File("build/danmaku-renders").also { it.mkdirs() }, "$name.png")
            out.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            val inked = inkedPixelCount(image)
            image.close()
            println("DANMAKU_RENDER_OUT: ${out.absolutePath} inkedPixels=$inked")
            assertTrue(
                inked > MIN_INKED_SAMPLES,
                "画面几乎是空的（落墨采样点 $inked）：弹幕绘制层没画东西出来",
            )
            return out
        } finally {
            scene.close()
        }
    }

    /**
     * 走一帧：喂位置采样 → 渲染 → 等够这一步的墙钟时间。
     *
     * 采样与帧时刻同速推进，tracker 便每帧重锚定到精确位置，不必靠外推
     * （外推最远只看 `MAX_LOOKAHEAD_MS`，够不到 1.5s / 2.6s）。
     */
    private fun tickScene(
        scene: ImageComposeScene,
        session: DanmakuSession,
        positionMs: Long,
    ): Image {
        session.onPlaybackSnapshot(
            positionMs = positionMs,
            durationMs = DURATION_MS,
            playbackSpeed = 1f,
            frozen = false,
        )
        val image = scene.render(BASE_NANOS + positionMs * NANOS_PER_MILLI)
        Thread.sleep(FRAME_STEP_MS)
        return image
    }

    /**
     * 落墨采样点数：**不预设背景色值**，而是取出现最多的那个颜色当背景。
     *
     * 两件事决定了必须这么写：
     * - 渲染位图的色彩空间与 sRGB 不是一一对应，写死背景色值逐位比大会误判；
     * - 弹幕几乎铺不满画面，所以"多数色"必然是背景，这个判据自洽且不需要额外常量。
     */
    private fun inkedPixelCount(image: Image): Int {
        val pixels = image.peekPixels() ?: return 0
        val sample = ArrayList<Int>(
            (image.width / SAMPLE_STRIDE) * (image.height / SAMPLE_STRIDE),
        )
        for (y in 0 until image.height step SAMPLE_STRIDE) {
            for (x in 0 until image.width step SAMPLE_STRIDE) {
                sample += pixels.getColor(x, y)
            }
        }
        val background = sample.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: return 0
        return sample.count { colorDistance(it, background) > COLOR_EPSILON }
    }

    private fun colorDistance(a: Int, b: Int): Int = maxOf(
        abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)),
        abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)),
        abs((a and 0xFF) - (b and 0xFF)),
    )

    @Test
    fun `滚动弹幕入场`() {
        withSession { session -> renderScene("scroll-0p6s", session, atPositionMs = 600L) }
    }

    @Test
    fun `密集段落加固定弹幕`() {
        withSession { session -> renderScene("busy-1p5s", session, atPositionMs = 1_500L) }
    }

    @Test
    fun `后段仍有弹幕在轨`() {
        withSession { session -> renderScene("tail-2p6s", session, atPositionMs = 2_600L) }
    }

    /**
     * 每次出一个干净的 session：引擎带游标与轨道占用，复用同一份会把上一张图的状态漏进来。
     */
    private fun withSession(block: (DanmakuSession) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val session = newSession(scope)
        // 先喂一次快照：装载（假日不挂起，Unconfined 就地跑完）+ 位置锚点都在这一步建立
        session.onPlaybackSnapshot(
            positionMs = 0L,
            durationMs = DURATION_MS,
            playbackSpeed = 1f,
            frozen = false,
        )
        try {
            block(session)
        } finally {
            session.dispose()
            scope.cancel()
        }
    }

    private companion object {
        /** 帧时刻起点：非零即可，取 1s 让日志里的毫秒数直接可读。 */
        const val BASE_NANOS = 1_000_000_000L
        const val NANOS_PER_MILLI = 1_000_000L

        /** 步长：约 20Hz，比 vsync 粗三倍，但远小于引擎的迟到窗口，不会因此把弹幕丢光。 */
        const val FRAME_STEP_MS = 50L

        /** 出图前多推几帧：`withFrameNanos` 写进的帧时刻要下一帧才被 Canvas 看到。 */
        const val SETTLE_FRAMES = 3L

        const val DURATION_MS = 60_000L

        const val SAMPLE_STRIDE = 2
        /** 单通道容差：挡住色彩空间/抗锯齿造成的微差；字与描边相对深灰底是几十到两百的差。 */
        const val COLOR_EPSILON = 24

        /** 采样网格里（1280/2 × 720/2 = 230400 个点）少于此数就视为整屏没落墨。 */
        const val MIN_INKED_SAMPLES = 200
    }
}
