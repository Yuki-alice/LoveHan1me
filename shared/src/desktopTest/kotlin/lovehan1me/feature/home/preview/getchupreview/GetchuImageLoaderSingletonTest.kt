package lovehan1me.feature.home.preview.getchupreview

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
 * A2 守卫：getchu 图片加载器必须是**进程级单例**（与通用加载器的 A1 同构）。
 *
 * 改之前 `rememberGetchuImageLoader` 每次调用点各建一个 ImageLoader（含独立
 * Coil 缓存）；iOS 侧更重，每次新建一个 `HttpClient(Darwin)`。这组用例把
 * "共享一份"钉成可执行的契约。反向验证：去掉缓存改成每次新建，
 * `连续两次取用返回同一实例` 与 `并发首次取用只有一个实例` 转红。
 */
class GetchuImageLoaderSingletonTest {

    private val context: PlatformContext = PlatformContext.INSTANCE

    @BeforeTest
    fun setUp() {
        resetGetchuImageLoaderForTest()
    }

    @AfterTest
    fun tearDown() {
        resetGetchuImageLoaderForTest()
    }

    @Test
    fun `连续两次取用返回同一实例`() {
        val first = getchuImageLoaderOrNull(context, inspection = false)
        val second = getchuImageLoaderOrNull(context, inspection = false)

        assertNotNull(first, "首次取用没构造出 ImageLoader")
        assertSame(first, second, "两次取用不是同一实例：getchu 加载器没被进程级缓存")
    }

    @Test
    fun `复位之后再取得到新实例`() {
        val first = getchuImageLoaderOrNull(context, inspection = false)
        assertNotNull(first, "首次取用没构造出 ImageLoader")

        resetGetchuImageLoaderForTest()
        val second = getchuImageLoaderOrNull(context, inspection = false)

        assertNotNull(second, "复位后没构造出 ImageLoader")
        assertNotSame(first, second, "复位无效：拿到的仍是复位前的实例")
    }

    @Test
    fun `并发首次取用只有一个实例`() {
        val threadCount = 16
        val startGate = CountDownLatch(1)
        val doneGate = CountDownLatch(threadCount)
        val results: MutableList<ImageLoader> = Collections.synchronizedList(mutableListOf())

        repeat(threadCount) { index ->
            thread(name = "getchu-loader-race-$index", isDaemon = false) {
                try {
                    startGate.await()
                    getchuImageLoaderOrNull(context, inspection = false)?.let(results::add)
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
    fun `inspection模式不提供共享实例`() {
        assertNull(
            getchuImageLoaderOrNull(context, inspection = true),
            "预览模式拿到了共享实例：预览各自的 context 会被泄漏给真实 UI",
        )
    }
}
