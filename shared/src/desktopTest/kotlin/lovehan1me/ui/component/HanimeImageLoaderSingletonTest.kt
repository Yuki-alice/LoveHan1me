package lovehan1me.ui.component

import coil3.ImageLoader
import coil3.PlatformContext
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A1 守卫：通用图片加载器必须是**进程级单例**。
 *
 * ## 它防的是什么
 * 改之前 `rememberHanimeImageLoader` 内部用 `remember(context, isInspectionMode) { … }` 构造
 * `ImageLoader`，而 `remember` 的作用域是**调用点** —— 8 个调用点各持一份，每份自带独立的
 * Coil 内存/磁盘缓存。后果是同一张封面在不同屏幕/单元格各解码一份、缓存互不命中，
 * 快滑时内存抖动。这组用例把"共享一份"钉成可执行的契约。
 *
 * ## 为什么断言 identity 而不是行为
 * "缓存命中率"没法在单测里稳定度量（要真实网络与解码）。但"是不是同一个实例"是这件事的
 * **充分**条件：缓存挂在实例上，实例同一 ⇒ 缓存同一。所以这里断言 `assertSame`。
 *
 * ## 反向验证（已实测）
 * 把 [hanimeImageLoaderOrNull] 的缓存去掉、改成每次 `ImageLoader.Builder(context)…build()`，
 * `连续两次取用返回同一实例` 与 `并发首次取用只有一个实例` 两条都会转红。
 *
 * 复位：单例是进程级的，故每个用例前后都调 [resetHanimeImageLoaderForTest]，避免彼此串。
 */
class HanimeImageLoaderSingletonTest {

    /** desktopTest 里没有 Composition，用 Coil 的平台 context 单例即可。 */
    private val context: PlatformContext = PlatformContext.INSTANCE

    @BeforeTest
    fun setUp() {
        resetHanimeImageLoaderForTest()
    }

    @AfterTest
    fun tearDown() {
        resetHanimeImageLoaderForTest()
    }

    @Test
    fun `D1 连续两次取用返回同一实例`() {
        val first = hanimeImageLoaderOrNull(context, inspection = false)
        val second = hanimeImageLoaderOrNull(context, inspection = false)

        assertNotNull(first, "首次取用没构造出 ImageLoader")
        assertSame(
            first,
            second,
            "两次取用不是同一实例：ImageLoader 没被进程级缓存，" +
                "同一张封面会在不同调用点各解码一份、缓存互不命中",
        )
    }

    @Test
    fun `D2 复位之后再取得到新实例`() {
        val first = hanimeImageLoaderOrNull(context, inspection = false)
        assertNotNull(first, "首次取用没构造出 ImageLoader")

        resetHanimeImageLoaderForTest()
        val second = hanimeImageLoaderOrNull(context, inspection = false)

        assertNotNull(second, "复位后没构造出 ImageLoader")
        assertNotSame(first, second, "复位无效：拿到的仍是复位前的实例，用例之间会互相串")
    }

    @Test
    fun `D3 并发首次取用只有一个实例`() {
        val threadCount = 16
        val startGate = CountDownLatch(1)
        val doneGate = CountDownLatch(threadCount)
        // 故意用同一个锁保护结果收集，避免断言依赖收集容器本身是否线程安全。
        val results: MutableList<ImageLoader> = Collections.synchronizedList(mutableListOf())

        repeat(threadCount) { index ->
            thread(name = "hanime-loader-race-$index", isDaemon = false) {
                try {
                    startGate.await()
                    hanimeImageLoaderOrNull(context, inspection = false)?.let(results::add)
                } finally {
                    doneGate.countDown()
                }
            }
        }
        startGate.countDown()
        assertTrue(doneGate.await(30, TimeUnit.SECONDS), "并发取用 30s 内没跑完")

        assertEquals(threadCount, results.size, "有线程没拿到 ImageLoader")
        val first = results.first()
        results.forEachIndexed { index, loader ->
            assertSame(first, loader, "第 $index 个线程拿到的是另一个实例：单例首取有竞态")
        }
    }

    @Test
    fun `D4 inspection 模式不提供共享实例`() {
        assertNull(
            hanimeImageLoaderOrNull(context, inspection = true),
            "预览模式拿到了共享实例：预览各自的 context 会被泄漏给真实 UI",
        )
        assertNull(
            hanimeImageLoaderOrNull(context, inspection = true),
            "预览模式拿到了共享实例（第二次）",
        )
    }

    @Test
    fun `D5 inspection 调用不会污染进程级单例`() {
        // 先一律走预览分支若干次，真实分支必须仍然构造出共享实例，
        // 且之后取到的仍是同一个 —— 证明预览既没被缓存、也没把缓存占掉。
        repeat(3) {
            assertNull(hanimeImageLoaderOrNull(context, inspection = true))
        }
        val real = hanimeImageLoaderOrNull(context, inspection = false)
        assertNotNull(real, "预览调用之后真实分支没构造出实例")
        assertSame(
            real,
            hanimeImageLoaderOrNull(context, inspection = false),
            "预览调用污染了单例：真实分支拿到的不是同一个实例",
        )
    }
}
