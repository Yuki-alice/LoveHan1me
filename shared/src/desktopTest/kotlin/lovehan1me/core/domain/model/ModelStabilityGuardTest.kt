package lovehan1me.core.domain.model

import java.io.File
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 核心模型稳定性护栏。
 *
 * `HanimeVideo` 被声明为 `@Immutable`，这是给 Compose 编译器的**承诺**：实例构造后不可变，
 * 因而在组合中可被跳过（skippable）。一旦有人把某个字段改回 `var`、或把字段声明类型换回
 * 具体可变集合类，注解就从「契约」退化成「谎言」——编译器会基于错误的承诺做跳过优化，
 * 埋下「数据变了但不重组」的幽灵 bug，且这种 bug 在运行期极难定位。
 *
 * 本测试把这条契约变成可执行断言，破了就当场失败：
 *
 *  1. [HanimeVideo.MyList] / [HanimeVideo.MyList.MyListInfo] 的状态字段必须 `val`
 *     —— 用反射读 backing field 的 `final` 修饰符钉死（`var` 的字段非 final，`val` 是）。
 *  2. [HanimeVideo.videoUrls] 的**声明返回类型**必须是只读的 `java.util.Map`
 *     —— typealias 会展开进 JVM 方法签名：`Map` → `java.util.Map`，`LinkedHashMap` →
 *     `java.util.LinkedHashMap`。
 *  3. `@Immutable` 注解必须**紧邻**声明 `data class HanimeVideo`。
 *  4. [HanimeVideo]、[HanimeVideo.MyList]、[HanimeVideo.MyList.MyListInfo] 的**所有实例
 *     字段**必须 `val`——用反射遍历 `declaredFields` 逐个校验 `final`。
 *     第 ① 条只钉住了两个"曾经是 var"的字段；`@Immutable` 承诺的是"构造后可达状态不再
 *     变化"，因此**类里任何一个字段** (如 `videoUrls`) 若被改成 `var`，同样会让注解退化成
 *     谎言。第 ④ 条补的就是这个空档：它把"该类自身字段皆 final"这半条契约变成可执行断言。
 *
 * 为什么落在 desktopTest：①/②/④ 需要 JVM 反射，③ 需要 `java.io.File` 读源码树，
 * common 源集都不具备。源码扫描的定位法沿用仓里既有的 `ModuleLayeringTest`（向上找
 * `settings.gradle.kts` 作为仓库根）。
 *
 * ⚠️ 注意 `@Immutable` 的 retention 是 BINARY，**Java 反射看不到它**，所以第 ③ 条
 * 只能扫源码，不能靠反射。
 */
class ModelStabilityGuardTest {

    // ---------------------------------------------------------------------
    // ① 反射钉 `val`：状态字段的 backing field 必须 final
    // ---------------------------------------------------------------------

    @Test
    fun `MyList_state fields are val (final backing field)`() {
        val isWatchLater = HanimeVideo.MyList::class.java.getDeclaredField("isWatchLater")
        assertTrue(
            isWatchLater.modifiers and Modifier.FINAL != 0,
            "契约被破坏：HanimeVideo.MyList.isWatchLater 必须是 val（其 backing field 应为 " +
                "final）。实测修饰符=${Modifier.toString(isWatchLater.modifiers)}。" +
                "改回 var 会让 HanimeVideo 的 @Immutable 承诺变成谎言，Compose 会错误跳过重组。",
        )

        val isSelected = HanimeVideo.MyList.MyListInfo::class.java.getDeclaredField("isSelected")
        assertTrue(
            isSelected.modifiers and Modifier.FINAL != 0,
            "契约被破坏：HanimeVideo.MyList.MyListInfo.isSelected 必须是 val（其 backing field " +
                "应为 final）。实测修饰符=${Modifier.toString(isSelected.modifiers)}。" +
                "改回 var 会让 HanimeVideo 的 @Immutable 承诺变成谎言，Compose 会错误跳过重组。",
        )
    }

    // ---------------------------------------------------------------------
    // ② 反射钉不可变声明类型：getVideoUrls() 必须声明返回 java.util.Map
    // ---------------------------------------------------------------------

    @Test
    fun `videoUrls getter declares read-only java_util_Map`() {
        val returnType: Class<*> = HanimeVideo::class.java.getMethod("getVideoUrls").returnType
        val mapClass: Class<*> = Class.forName("java.util.Map")
        val linkedHashMapClass: Class<*> = Class.forName("java.util.LinkedHashMap")

        assertTrue(
            returnType == mapClass,
            "契约被破坏：HanimeVideo.videoUrls 的声明返回类型必须是只读的 java.util.Map，" +
                "实测为 ${returnType.name}。类型一旦回退到具体可变实现类，消费它的模型会被 " +
                "Compose 判定为 unstable，详情页的跳过优化随之丢失。",
        )

        assertTrue(
            returnType != linkedHashMapClass,
            "契约被破坏：HanimeVideo.videoUrls 的声明返回类型不得是具体可变实现类 " +
                "java.util.LinkedHashMap（其可变性会让 HanimeVideo 被判 unstable）。" +
                "ResolutionLinkMap 的 typealias 必须指向 Map，而非 LinkedHashMap。",
        )
    }

    // ---------------------------------------------------------------------
    // ③ 源码扫描钉 `@Immutable`：必须紧邻声明 data class HanimeVideo
    // ---------------------------------------------------------------------

    @Test
    fun `HanimeVideo is annotated with adjacent @Immutable`() {
        val source = hanimeVideoSourceFile()
        assertTrue(source.isFile, "找不到 HanimeVideo 源码文件：${source.path}")

        val lines = source.readLines()
        val declIndex = lines.indexOfFirst { it.trim().startsWith("data class HanimeVideo") }
        assertTrue(
            declIndex >= 0,
            "在 ${source.name} 中找不到 `data class HanimeVideo` 声明，无法校验 @Immutable 契约。",
        )

        // 只收集**紧邻**声明上方的连续注解行，避免文件中别处提及 "@Immutable" 造成假绿。
        val annotations = mutableListOf<String>()
        var cursor = declIndex - 1
        while (cursor >= 0 && lines[cursor].trim().startsWith("@")) {
            annotations += lines[cursor].trim()
            cursor--
        }

        assertTrue(
            annotations.any { it == "@Immutable" || it.startsWith("@Immutable(") },
            "契约被破坏：HanimeVideo 的声明上方必须紧邻 @Immutable（androidx.compose.runtime.Immutable），" +
                "实测其上的注解块为 $annotations。缺了它，Compose 不会再信任本类的不可变性，" +
                "详情页重建时整棵子树都将被迫重组。",
        )
    }

    // ---------------------------------------------------------------------
    // ④ 反射钉"整个不可变子树的所有实例字段皆 final"
    // ---------------------------------------------------------------------

    /**
     * `@Immutable` 承诺覆盖**整个可达子树**，因此契约主体是这三个类。任何一者的任何一个
     * 非 final 实例字段，都会让承诺失效。
     */
    @Test
    fun `all instance fields across the immutable tree are val (final backing field)`() {
        val targets: List<Class<*>> = listOf(
            HanimeVideo::class.java,
            HanimeVideo.MyList::class.java,
            HanimeVideo.MyList.MyListInfo::class.java,
        )

        val violations = targets.flatMap { clazz ->
            clazz.declaredFields
                .asSequence()
                // 只校验**实例**字段：静态字段（如 @Serializable 生成的 Companion / $serializer）
                // 不参与"构造后可达状态"，不属于本契约范围。
                .filterNot { Modifier.isStatic(it.modifiers) }
                // 合成字段由编译器生成，不是源码里声明的属性，不应据此判定契约。
                .filterNot { it.isSynthetic }
                .filterNot { Modifier.isFinal(it.modifiers) }
                .map { field ->
                    "${clazz.simpleName}.${field.name} 必须是 val（实测修饰符=" +
                        "${Modifier.toString(field.modifiers)}）"
                }
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "契约被破坏：HanimeVideo 及其可达嵌套类（MyList / MyListInfo）的**所有实例字段**都必须是 " +
                "val（backing field 为 final），才能支撑 @Immutable 的承诺。违规字段：\n" +
                violations.joinToString("\n") +
                "\n把任一字段改成 var 都会让 @Immutable 从契约退化为谎言，Compose 会基于错误的" +
                "不可变假设跳过重组，界面出现'数据变了但不刷新'的幽灵 bug。",
        )
    }

    /** 从当前工作目录向上找 `settings.gradle.kts` 作为仓库根（沿用 ModuleLayeringTest 的定位法）。 */
    private fun repoRoot(): File {
        var dir = File(".").absoluteFile
        while (dir.parentFile != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("从 ${File(".").absoluteFile} 向上找不到 settings.gradle.kts")
    }

    private fun hanimeVideoSourceFile(): File = File(
        repoRoot(),
        "shared/src/commonMain/kotlin/lovehan1me/core/domain/model/HanimeVideo.kt",
    )
}
