package lovehan1me.video.contract

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine

/**
 * 防御版 [combine]。
 *
 * ## 背景
 * 线上偶发进程闪退：
 * ```
 * java.lang.NullPointerException: Cannot invoke "Flow.collect(...)" because "this.$flows[this.$i]" is null
 *     at kotlinx.coroutines.flow.internal.CombineKt$combineInternal$2$1.invokeSuspend(Combine.kt:28)
 * ```
 *
 * `combineInternal` 遍历 `flows[i].collect` 时拿到了 `null`。Kotlin 编译器对非空
 * `Flow` 形参在源码层不允许传 null，因此这类 null 只可能来自两条路径：
 *  1. **可空 Flow 属性未兜底就塞进 combine**（如 `player.chapters ?: flowOf(...)` 漏写，
 *     或 DataStore 的 `.data` 工厂属性在并发下被读成不一致实例）；
 *  2. **R8 / 协程版本错位**导致 vararg 数组在运行期出现空洞。
 *
 * 不管哪条，null 一旦进 `combineInternal` 就是**进程级闪退**。这里在收集层把
 * `NullPointerException` 吞掉，让下游 `StateFlow` 停在最后一个有效值（或初始值），
 * 用"停更"换"不崩"。
 *
 * 契约层没有日志门面，错误走 `println`（Android 落 logcat / 桌面落 stdout），
 * 前缀固定便于检索。
 *
 * 用法：把项目内的 `combine(...)` 直接换成 `safeCombine(...)`，签名完全一致。
 */
private const val LOG_PREFIX = "[FlowCombine]"

@PublishedApi
internal fun <T> Flow<T>.guardCombineNull(): Flow<T> = catch { cause ->
    if (cause is CancellationException) throw cause
    if (cause is NullPointerException) {
        println(
            "$LOG_PREFIX combine 内部出现 null Flow（可空 Flow 未兜底 / R8-协程版本错位），" +
                "已吞掉避免进程崩溃：${cause.stackTraceToString().take(400)}",
        )
    } else {
        throw cause
    }
}

fun <T1, T2, R> safeCombine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    transform: suspend (T1, T2) -> R,
): Flow<R> = combine(flow, flow2, transform).guardCombineNull()

fun <T1, T2, T3, R> safeCombine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    transform: suspend (T1, T2, T3) -> R,
): Flow<R> = combine(flow, flow2, flow3, transform).guardCombineNull()

fun <T1, T2, T3, T4, R> safeCombine(
    flow: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    flow4: Flow<T4>,
    transform: suspend (T1, T2, T3, T4) -> R,
): Flow<R> = combine(flow, flow2, flow3, flow4, transform).guardCombineNull()

/**
 * 变长版本：`combine(vararg flows) { array -> ... }` 的防御替代。
 *
 * 与标准库一致，`transform` 收到的 `Array<T>` 长度等于 `flows.size`。
 *
 * 注意：标准库的 `combine(vararg)` / `combine(Iterable)` 都是 `inline fun <reified T>`，
 * 从普通函数里转调会触发 "Cannot use 'T' as reified type parameter"。故本函数也声明为
 * `inline`，让 `reified T` 沿调用链传下去。
 */
inline fun <reified T, R> safeCombine(
    vararg flows: Flow<T>,
    crossinline transform: suspend (Array<T>) -> R,
): Flow<R> = combine(flows.toList()) { values -> transform(values) }.guardCombineNull()
