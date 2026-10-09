package lovehan1me.ui.component.lazy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import lovehan1me.ui.preview.HanimePreviewTheme
import org.jetbrains.skia.Image
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * P1 #12a 守卫：wrapper 的 `enableItemPlacementAnimation` 在重排时必须让 item 逐帧迁移。
 *
 * 手法（虚拟时钟、无真实时间依赖，全量并行跑也确定性复现）：静置 → 交换两项
 * （无增删）→ 连拍 12 帧（16ms 步进），与上一帧比对。动画生效 = 重排后有多帧
 * 持续推进的位移；无动画 = 首帧即终态、后续全零（嵌套手写 `animateItem` 的
 * 静默失效即此形态 —— 逐帧实测过，两版对照见 git 历史）。
 *
 * 两条用例互为（正/负）对照，缺一不可：
 * - 正向：开启开关 → 位移帧 ≥ 3（动画真实推进）；
 * - 负向：不开开关 → 位移帧 ≤ 1（只允许"交换那一帧"的静态差异）。负向角色是
 *   区分力哨兵：若它转红（>1），说明场景里混进了别的动画，正向断言将失去意义。
 */
class LazyItemPlacementAnimationTest {

    @Test
    fun `开启开关_重排后逐帧推进`() {
        val movingFrames = countMovingFrames(enablePlacement = true)
        assertTrue(
            movingFrames >= 3,
            "开启 enableItemPlacementAnimation 后重排应有连续位移帧（实测 $movingFrames 帧）：动画没挂到 item 根节点上？",
        )
    }

    @Test
    fun `不开开关_重排瞬移（对照）`() {
        val movingFrames = countMovingFrames(enablePlacement = false)
        assertTrue(
            movingFrames <= 1,
            "未开启开关时不应出现逐帧位移（实测 $movingFrames 帧）：场景里混入了别的动画，守卫失去区分力",
        )
    }

    private fun countMovingFrames(enablePlacement: Boolean): Int {
        val items = mutableStateOf(listOf("A", "B", "C", "D"))
        val scene = ImageComposeScene(
            width = 400,
            height = 700,
            density = Density(2f),
            content = {
                HanimePreviewTheme(modifier = Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        ScenarioItems(items, enablePlacement)
                    }
                }
            },
        )
        return try {
            scene.render(0L)
            scene.render(1_000_000_000L)
            var prev = scene.render(1_100_000_000L)

            // 交换 A / C 两项：无增删、无 fade，位移只可能来自 placement 动画。
            items.value = listOf("C", "B", "A", "D")
            var t = 1_120_000_000L
            var moving = 0
            repeat(12) {
                val frame = scene.render(t)
                if (diffPixels(prev, frame) > 0) moving++
                prev = frame
                t += 16_000_000L
            }
            println("PLACEMENT_ANIM_OUT: enable=$enablePlacement movingFrames=$moving")
            moving
        } finally {
            scene.close()
        }
    }

    @Composable
    private fun ScenarioItems(
        items: MutableState<List<String>>,
        enablePlacement: Boolean,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            enableItemPlacementAnimation = enablePlacement,
        ) {
            items(items.value, key = { it }) { s ->
                Box(modifier = Modifier.fillMaxWidth().height(72.dp)) {
                    Text(text = s)
                }
            }
        }
    }

    private fun diffPixels(a: Image, b: Image): Int {
        val pa = a.peekPixels() ?: return 0
        val pb = b.peekPixels() ?: return 0
        var count = 0
        for (y in 0 until a.height step 2) {
            for (x in 0 until a.width step 2) {
                val ca = pa.getColor(x, y)
                val cb = pb.getColor(x, y)
                val d = maxOf(
                    abs((ca shr 16 and 0xFF) - (cb shr 16 and 0xFF)),
                    abs((ca shr 8 and 0xFF) - (cb shr 8 and 0xFF)),
                    abs((ca and 0xFF) - (cb and 0xFF)),
                )
                if (d > 24) count++
            }
        }
        return count
    }
}
