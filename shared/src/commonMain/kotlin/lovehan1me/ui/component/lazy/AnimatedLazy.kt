package lovehan1me.ui.component.lazy

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.foundation.lazy.LazyColumn as FoundationLazyColumn
import androidx.compose.foundation.lazy.LazyRow as FoundationLazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid as FoundationLazyVerticalGrid

/**
 * 带通用 item 动画的 LazyColumn 封装。
 *
 * 目标是尽量兼容原生 `LazyColumn` / `LazyRow` / `LazyVerticalGrid` 的常用调用方式，
 * 并通过统一的轻量入场动画让列表在大多数页面里获得更自然的观感。
 *
 * 两套**互相独立**的动画开关（都默认关，逐列表 opt-in）：
 * - `enableItemAnimation`：轻量错峰入场（alpha + 0.985 缩放，motionScheme fast 档）。
 *   注意它会再包一层容器，与下面的 placement 动画同开时后者不在根节点上（见 `AnimatedListItem` 注释）。
 * - `enableItemPlacementAnimation`：M3E 空间连续性（`Modifier.animateItem` —— 项增删 / 重排
 *   时位置弹簧迁移 + 进出场 fade）。
 *
 * ⚠️ placement 动画必须挂在 **item 的根节点**上：foundation 通过根 placeable 的 parentData
 * 查找动画规格（`LazyLayoutAnimationSpecsNode`），**包在任何内层都会静默失效**（离屏逐帧
 * 实测：嵌套版 12 帧零位移，根节点版 12 帧位置持续推进）。因此任何"在业务 item 内容里手写
 * `Modifier.animateItem()`"的写法在本封装下都不生效（业务内容必然在 wrapper 自建的 Box 之下），
 * 一律改用本开关。守卫测试：`LazyItemPlacementAnimationTest`。
 */
@Composable
fun LazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    userScrollEnabled: Boolean = true,
    enableItemAnimation: Boolean = false,
    enableItemPlacementAnimation: Boolean = false,
    staggerStepMillis: Int = 12,
    maxStaggerIndex: Int = 6,
    content: AnimatedLazyListScope.() -> Unit,
) {
    CompositionLocalProvider(LocalItemPlacementAnimation provides enableItemPlacementAnimation) {
        FoundationLazyColumn(
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            userScrollEnabled = userScrollEnabled,
        ) {
            AnimatedLazyListScope(
                scope = this,
                enableItemAnimation = enableItemAnimation,
                staggerStepMillis = staggerStepMillis,
                maxStaggerIndex = maxStaggerIndex,
            ).content()
        }
    }
}

@Composable
fun LazyRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    reverseLayout: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal = if (!reverseLayout) Arrangement.Start else Arrangement.End,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    userScrollEnabled: Boolean = true,
    enableItemAnimation: Boolean = false,
    enableItemPlacementAnimation: Boolean = false,
    staggerStepMillis: Int = 12,
    maxStaggerIndex: Int = 6,
    content: AnimatedLazyListScope.() -> Unit,
) {
    CompositionLocalProvider(LocalItemPlacementAnimation provides enableItemPlacementAnimation) {
        FoundationLazyRow(
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment,
            userScrollEnabled = userScrollEnabled,
        ) {
            AnimatedLazyListScope(
                scope = this,
                enableItemAnimation = enableItemAnimation,
                staggerStepMillis = staggerStepMillis,
                maxStaggerIndex = maxStaggerIndex,
            ).content()
        }
    }
}

@Composable
fun LazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    userScrollEnabled: Boolean = true,
    enableItemAnimation: Boolean = false,
    enableItemPlacementAnimation: Boolean = false,
    staggerStepMillis: Int = 12,
    maxStaggerIndex: Int = 6,
    content: AnimatedLazyGridScope.() -> Unit,
) {
    CompositionLocalProvider(LocalItemPlacementAnimation provides enableItemPlacementAnimation) {
        FoundationLazyVerticalGrid(
            columns = columns,
            modifier = modifier,
            state = state,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            userScrollEnabled = userScrollEnabled,
        ) {
            AnimatedLazyGridScope(
                scope = this,
                enableItemAnimation = enableItemAnimation,
                staggerStepMillis = staggerStepMillis,
                maxStaggerIndex = maxStaggerIndex,
            ).content()
        }
    }
}

/**
 * placement 动画（M3E 空间连续性）的默认弹簧 —— 与项目既有口径一致
 * （`HomeCategoryLayoutDialog` 的项重排动画同参：medium-low + 无回弹）。
 */
private val DefaultItemPlacementSpec = spring<IntOffset>(
    stiffness = Spring.StiffnessMediumLow,
    dampingRatio = Spring.DampingRatioNoBouncy,
)

/**
 * 逐列表开关值的载体。经 [CompositionLocalProvider] 下发到 item 构造处，
 * 避免往两个 Scope 类的全部 `item` / `items` / `itemsIndexed` 重载里逐个透传参数。
 */
private val LocalItemPlacementAnimation = compositionLocalOf { false }

class AnimatedLazyListScope internal constructor(
    private val scope: LazyListScope,
    private val enableItemAnimation: Boolean,
    private val staggerStepMillis: Int,
    private val maxStaggerIndex: Int,
) {
    fun item(
        key: Any? = null,
        contentType: Any? = null,
        content: @Composable LazyItemScope.() -> Unit,
    ) {
        scope.item(key = key, contentType = contentType) {
            AnimatedListItem(
                enableItemAnimation = enableItemAnimation,
                animationIndex = 0,
                staggerStepMillis = staggerStepMillis,
                maxStaggerIndex = maxStaggerIndex,
                content = content,
            )
        }
    }

    fun items(
        count: Int,
        key: ((index: Int) -> Any)? = null,
        contentType: (index: Int) -> Any? = { null },
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit,
    ) {
        scope.items(
            count = count,
            key = key,
            contentType = contentType,
        ) { index ->
            AnimatedListItem(
                enableItemAnimation = enableItemAnimation,
                animationIndex = index,
                staggerStepMillis = staggerStepMillis,
                maxStaggerIndex = maxStaggerIndex,
            ) { itemContent(index) }
        }
    }

    fun <T> items(
        items: List<T>,
        key: ((item: T) -> Any)? = null,
        contentType: (item: T) -> Any? = { null },
        itemContent: @Composable LazyItemScope.(item: T) -> Unit,
    ) {
        scope.items(
            count = items.size,
            key = key?.let { itemKey -> { index -> itemKey(items[index]) } },
            contentType = { index -> contentType(items[index]) },
        ) { index ->
            AnimatedListItem(
                enableItemAnimation = enableItemAnimation,
                animationIndex = index,
                staggerStepMillis = staggerStepMillis,
                maxStaggerIndex = maxStaggerIndex,
            ) { itemContent(items[index]) }
        }
    }

    fun <T> itemsIndexed(
        items: List<T>,
        key: ((index: Int, item: T) -> Any)? = null,
        contentType: ((index: Int, item: T) -> Any?)? = null,
        itemContent: @Composable LazyItemScope.(index: Int, item: T) -> Unit,
    ) {
        scope.items(
            count = items.size,
            key = key?.let { itemKey -> { index -> itemKey(index, items[index]) } },
            contentType = { index -> contentType?.invoke(index, items[index]) },
        ) { index ->
            AnimatedListItem(
                enableItemAnimation = enableItemAnimation,
                animationIndex = index,
                staggerStepMillis = staggerStepMillis,
                maxStaggerIndex = maxStaggerIndex,
            ) { itemContent(index, items[index]) }
        }
    }
}

class AnimatedLazyGridScope internal constructor(
    private val scope: LazyGridScope,
    private val enableItemAnimation: Boolean,
    private val staggerStepMillis: Int,
    private val maxStaggerIndex: Int,
) {
    fun item(
        key: Any? = null,
        span: (LazyGridItemSpanScope.() -> GridItemSpan)? = null,
        contentType: Any? = null,
        content: @Composable LazyGridItemScope.() -> Unit,
    ) {
        scope.item(
            key = key,
            span = span,
            contentType = contentType,
        ) {
            AnimatedGridItem(
                enableItemAnimation = enableItemAnimation,
                animationIndex = 0,
                staggerStepMillis = staggerStepMillis,
                maxStaggerIndex = maxStaggerIndex,
                content = content,
            )
        }
    }

    fun items(
        count: Int,
        key: ((index: Int) -> Any)? = null,
        span: (LazyGridItemSpanScope.(index: Int) -> GridItemSpan)? = null,
        contentType: (index: Int) -> Any? = { null },
        itemContent: @Composable LazyGridItemScope.(index: Int) -> Unit,
    ) {
        scope.items(
            count = count,
            key = key,
            span = span,
            contentType = contentType,
        ) { index ->
            AnimatedGridItem(
                enableItemAnimation = enableItemAnimation,
                animationIndex = index,
                staggerStepMillis = staggerStepMillis,
                maxStaggerIndex = maxStaggerIndex,
            ) { itemContent(index) }
        }
    }

    fun <T> items(
        items: List<T>,
        key: ((item: T) -> Any)? = null,
        span: (LazyGridItemSpanScope.(item: T) -> GridItemSpan)? = null,
        contentType: (item: T) -> Any? = { null },
        itemContent: @Composable LazyGridItemScope.(item: T) -> Unit,
    ) {
        scope.items(
            count = items.size,
            key = key?.let { itemKey -> { index -> itemKey(items[index]) } },
            span = span?.let { itemSpan -> { index -> itemSpan.invoke(this, items[index]) } },
            contentType = { index -> contentType(items[index]) },
        ) { index ->
            AnimatedGridItem(
                enableItemAnimation = enableItemAnimation,
                animationIndex = index,
                staggerStepMillis = staggerStepMillis,
                maxStaggerIndex = maxStaggerIndex,
            ) { itemContent(items[index]) }
        }
    }

    fun <T> itemsIndexed(
        items: List<T>,
        key: ((index: Int, item: T) -> Any)? = null,
        span: (LazyGridItemSpanScope.(index: Int, item: T) -> GridItemSpan)? = null,
        contentType: ((index: Int, item: T) -> Any?)? = null,
        itemContent: @Composable LazyGridItemScope.(index: Int, item: T) -> Unit,
    ) {
        scope.items(
            count = items.size,
            key = key?.let { itemKey -> { index -> itemKey(index, items[index]) } },
            span = span?.let { itemSpan ->
                { index ->
                    itemSpan.invoke(
                        this,
                        index,
                        items[index]
                    )
                }
            },
            contentType = { index -> contentType?.invoke(index, items[index]) },
        ) { index ->
            AnimatedGridItem(
                enableItemAnimation = enableItemAnimation,
                animationIndex = index,
                staggerStepMillis = staggerStepMillis,
                maxStaggerIndex = maxStaggerIndex,
            ) { itemContent(index, items[index]) }
        }
    }
}

@Composable
private fun LazyItemScope.AnimatedListItem(
    enableItemAnimation: Boolean,
    animationIndex: Int,
    staggerStepMillis: Int,
    maxStaggerIndex: Int,
    content: @Composable LazyItemScope.() -> Unit,
) {
    // placement 动画的规格必须落在 item 根节点（本函数自建的 Box）上 —— 见文件头注释。
    // 错峰入场开启时本 Box 会处在 graphicsLayer 容器之内，此时 placement 动画不生效
    // （当前全仓没有同开两者的调用点；真出现需求时把 itemModifier 外提到容器根即可）。
    val placementAnimation = LocalItemPlacementAnimation.current
    val itemModifier = if (placementAnimation) {
        Modifier.animateItem(placementSpec = DefaultItemPlacementSpec)
    } else {
        Modifier
    }
    AnimatedLazyItemContainer(
        enableItemAnimation = enableItemAnimation,
        animationIndex = animationIndex,
        staggerStepMillis = staggerStepMillis,
        maxStaggerIndex = maxStaggerIndex,
    ) {
        Box(modifier = itemModifier) {
            content()
        }
    }
}

@Composable
private fun LazyGridItemScope.AnimatedGridItem(
    enableItemAnimation: Boolean,
    animationIndex: Int,
    staggerStepMillis: Int,
    maxStaggerIndex: Int,
    content: @Composable LazyGridItemScope.() -> Unit,
) {
    // 同 AnimatedListItem：规格挂在 item 根节点 Box 上。
    val placementAnimation = LocalItemPlacementAnimation.current
    val itemModifier = if (placementAnimation) {
        Modifier.animateItem(placementSpec = DefaultItemPlacementSpec)
    } else {
        Modifier
    }
    AnimatedLazyItemContainer(
        enableItemAnimation = enableItemAnimation,
        animationIndex = animationIndex,
        staggerStepMillis = staggerStepMillis,
        maxStaggerIndex = maxStaggerIndex,
    ) {
        Box(modifier = itemModifier) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedLazyItemContainer(
    enableItemAnimation: Boolean,
    animationIndex: Int,
    staggerStepMillis: Int,
    maxStaggerIndex: Int,
    content: @Composable () -> Unit,
) {
    if (!enableItemAnimation) {
        content()
        return
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val clampedIndex = animationIndex.coerceIn(0, maxStaggerIndex)
        val delayMillis = (clampedIndex * staggerStepMillis).coerceAtLeast(0)
        if (delayMillis > 0) {
            kotlinx.coroutines.delay(delayMillis.toLong().milliseconds)
        }
        visible = true
    }
    val animatedAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        // 审计 P1：列表项入场不再写死 tween(100/180)，改走 motionScheme ——
        // 透明度是 effects 属性、缩放是 spatial 属性，档位取 fast（入场是高频动作，别拖）。
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "lazy-item-alpha",
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.985f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "lazy-item-scale",
    )
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = animatedAlpha
            scaleX = animatedScale
            scaleY = animatedScale
        }
    ) {
        content()
    }
}
