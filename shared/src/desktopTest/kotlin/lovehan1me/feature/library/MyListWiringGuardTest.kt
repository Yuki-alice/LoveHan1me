package lovehan1me.feature.library

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **接线守卫**：钉住「分页状态的每一次写入都真的经过分页闸门」这条**接线不变式**。
 *
 * ## 为什么需要它
 * `MyListPagingTest` 只覆盖 `MyListPaging.kt` 的**纯函数**——证明「闸门逻辑是对的」，但证明不了
 * 「生产代码真的在用闸门」。QA 的对抗实验：往 `launchPage` 注入一条直写，守卫全绿。本守卫把
 * 「所有写入都经过 `applyPageResponse` / `gate.isCurrent`」从**源码审阅的结论**变成可执行断言。
 *
 * ## 规则（对 `MyListSubViewModel.kt` 源码扫描）
 * 1. 找出两个状态宿主（`itemsFlow` / `mutableLoadedPageCount`）的**所有写入点**
 *    （`host.value = …` 赋值 与 `host.update { … }`），判定其**外层函数**（向上找最近的函数声明）。
 *    写入所在函数必须在允许清单 `{launchPage, deleteItem, clearMyListItems}` 内，否则规则 1 顶红。
 * 2. 提交函数（非豁免）的每一次写入，其值必须来自**闸门判定的产物**
 *    （`val X = applyPageResponse(...)` 的 `X`）——否则规则 2 顶红。
 * 3. 提交函数的函数体必须**确实引用闸门判定**（`applyPageResponse` / `isCurrent`），否则规则 3 顶红。
 *
 * ### 洞 1（已修）：写入点不许被静默丢弃
 * 写入若位于文件**首个 `fun` 之前**（类体 `init {}` / 属性初始化器），`enclosingFunction` 找不到函数。
 * 旧实现 `?: return@forEachIndexed` 会**悄悄丢掉**该写入点（既不计入也不报错）。现在改为记哨兵名
 * `<class-body>` 收进 `writes`（必不在允许清单 → 规则 1 顶红），并同时记入 `dropped` 供显式断言。
 *
 * ### 洞 2（已修）：跨行写入不许被漏扫
 * 旧实现**逐行**匹配 `itemsFlow.value=`，写入一旦跨行（`itemsFlow` 换行后 `.value = …`）就没有任何
 * 一行同时含 host 与 `value=`，该写入整体消失。现在 host 匹配对**整份源码**做正则（`\s*` 允许跨行），
 * 行号由「匹配起点之前的换行数」换算；`rhs` 仍取自**写入所在行**、行尾截断——因此跨行写法在 host 行
 * 取不到值 → `rhs == null` → 规则 2 顶红。
 *
 * ## 已知边界（不钉，刻意为之）
 * `HOSTS` **只含 `itemsFlow` / `mutableLoadedPageCount` 两个「右值门控」宿主**：
 *  - `mutableIsLoadingMore.value = !isRefresh && itemsFlow.value.isNotEmpty()`（`launchPage`）右值不来自闸门产物；
 *  - `itemsStateFlow.value = state`（`launchPage`）右值也不来自闸门产物；
 *  - `itemsStateFlow.value = state` 另有基于 `if (pagingGate.isCurrent(token))` 的**控制流门控**，行扫描判不了。
 * 若把它们并入 `HOSTS`，规则 2 会**假红**。故本守卫只钉右值门控的两个宿主。
 *
 * ## 自校验
 * `接线扫描器自校验_能报出植入的绕过写入` 用**内存合成源码**喂 `analyzeWiring` 并断言植入的直写会被报出。
 * 这样「扫描逻辑本身」也被钉住——谁改坏扫描器，是这条自校验转红，而不是整个守卫无声变绿。
 *
 * 跑法：`:shared:desktopTest --tests "lovehan1me.feature.library.MyListWiringGuardTest" --offline`
 */
class MyListWiringGuardTest {

    // ───────────────────────── 主守卫 ─────────────────────────

    @Test
    fun `分页状态写入必须经过分页闸门`() {
        val file = File(repoRoot(), WIRING_SRC)
        assertTrue(file.isFile, "找不到 MyListSubViewModel 源码：${file.path}")

        val r = analyzeWiring(file.readText(), WIRING_SRC)

        assertTrue(
            r.writes.isNotEmpty(),
            "在 $WIRING_SRC 扫描不到任何状态写入点——扫描逻辑已失效（会假绿）；" +
                "先用反向验证确认守卫仍然有效。",
        )
        // 洞 1：写入点必须能被归属（类体 / 初始化器的写入记哨兵并进 dropped，不许静默丢弃）。
        assertTrue(
            r.dropped.isEmpty(),
            "【洞1】有写入点无法归属到任何函数（类体 / 初始化器），可能是绕过闸门的通道：\n" +
                r.renderDropped(),
        )
        // 规则 1：写入所在的外层函数必须在允许清单内。
        assertTrue(
            r.outsideAllowlist.isEmpty(),
            "【规则1】以下写入不在允许清单内（新增 / 漂移的写入点，需人工确认是否绕过闸门）：\n" +
                r.render(r.outsideAllowlist),
        )
        // 规则 3：提交函数必须真的引用闸门判定，否则「在允许清单里」就是免检通道。
        assertTrue(
            r.nonGateCommits.isEmpty(),
            "【规则3】以下函数在允许清单内却未引用闸门判定（applyPageResponse / isCurrent）：" +
                "它们现在是免检通道，任何直写都能混过去：${r.nonGateCommits}",
        )
        // 规则 2：提交函数（非豁免）的每一次写入，其值必须来自闸门判定的产物。
        assertTrue(
            r.bypassing.isEmpty(),
            "【规则2】以下写入绕过了分页闸门（写入值未来自闸门产物，会重新引入旧响应并入 / " +
                "loadedPageCount 回退）：\n" + r.render(r.bypassing) +
                "\n（提交函数里允许写入的值只能来自 " +
                "${r.gateJustifiedVars.ifEmpty { setOf("applyPageResponse 的产物") }}）",
        )
    }

    // ───────────────────────── 自校验 ─────────────────────────

    @Test
    fun `接线扫描器自校验_能报出植入的绕过写入`() {
        val clean = analyzeWiring(SYNTHETIC_CLEAN, "synthetic/clean.kt")
        assertTrue(clean.writes.isNotEmpty(), "自校验（干净样本）：扫描不到任何写入点，扫描逻辑已失效。")
        assertTrue(
            !clean.hasViolations,
            "自校验（干净样本）**误报**：扫描器把正常代码判成了违规，守卫会假红。\n" + clean.fullReport(),
        )

        // 植入一条「类体直写」：修改前会被静默丢弃（假绿），修好后必须被报出。
        val dirty = analyzeWiring(SYNTHETIC_WITH_BYPASS, "synthetic/dirty.kt")
        assertTrue(
            dirty.hasViolations,
            "自校验失败：扫描器漏掉了植入的绕过写入——守卫会假绿，比没有守卫更糟。\n" + dirty.fullReport(),
        )
        assertTrue(
            dirty.dropped.isNotEmpty(),
            "植入的类体写入未被归入 dropped（洞 1 未封）：它可能被静默丢弃了。",
        )
    }

    // ───────────────────────── 注册表守卫 ─────────────────────────

    /**
     * `MyListPagingController` 的**实现者注册表**：新增实现者必须显式分类，逼人来决定它走闸门还是
     * 声明无分页——`LocalFav/LocalWatchLater` 完全在 `MyListSubViewModel.kt` 扫描范围之外，
     * 它们的「本地列表无分页」契约否则无人守。
     */
    @Test
    fun `MyListPagingController 实现者必须登记并分类`() {
        val dir = File(repoRoot(), LIB_DIR)
        assertTrue(dir.isDirectory, "找不到 ${dir.path}")

        val baseText = stripComments(File(dir, BASE_FILE).readText())
        val baseUsesGate = baseText.contains("PagingGate")

        val discovered = (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.name }
            .flatMap { f ->
                val text = stripComments(f.readText())
                pagingImplementers(text).map { it to f.name }
            }

        assertTrue(
            discovered.isNotEmpty(),
            "扫描不到任何 $LIB_DIR/*.kt 里的 MyListPagingController 实现者——扫描逻辑已失效（会假绿）。",
        )

        val violations = mutableListOf<String>()
        discovered.forEach { (name, fileName) ->
            when (name) {
                "MyListSubViewModel" ->
                    if (!baseUsesGate) violations += "基类 $fileName 未引用 PagingGate。"

                "FavSubViewModel", "WatchLaterSubViewModel" -> {
                    // 在线实现者：必须经基类（基类引用闸门），否则它自己得引用闸门。
                    val text = stripComments(File(dir, fileName).readText())
                    if (!text.contains("MyListSubViewModel(")) {
                        violations += "在线实现者 $name（$fileName）未经基类 MyListSubViewModel 收敛。"
                    } else if (!baseUsesGate) {
                        violations += "在线实现者 $name（$fileName）依赖的基类未引用 PagingGate。"
                    }
                }

                "LocalFavSubViewModel", "LocalWatchLaterSubViewModel" -> {
                    // 本地实现者：无分页 → loadNextPage 恒 false，且不得出现 PagingGate。
                    val text = stripComments(File(dir, fileName).readText())
                    if (!LOCAL_LOAD_NEXT_PAGE_FALSE.containsMatchIn(text)) {
                        violations += "本地实现者 $name（$fileName）的 `loadNextPage(): Boolean = false` 声明缺失或漂移。"
                    }
                    if (text.contains("PagingGate")) {
                        violations += "本地实现者 $name（$fileName）不应出现 PagingGate（本地列表无分页）。"
                    }
                }

                else -> violations +=
                    "未登记的 MyListPagingController 实现者：$name（$fileName）——" +
                        "请决定它走闸门（引用 PagingGate）还是声明无分页。"
            }
        }

        assertTrue(
            violations.isEmpty(),
            "MyListPagingController 实现者登记 / 分类违规：\n" + violations.joinToString("\n") { "  $it" },
        )
    }

    // ───────────────────────── 扫描实现 ─────────────────────────

    private data class WriteSite(
        val lineIndex: Int,
        val function: String,
        val rhs: String?,
        val raw: String,
    )

    private data class WiringResult(
        val srcLabel: String,
        val writes: List<WriteSite>,
        val dropped: List<Int>,
        val outsideAllowlist: List<WriteSite>,
        val nonGateCommits: List<String>,
        val bypassing: List<WriteSite>,
        val gateJustifiedVars: Set<String>,
    ) {
        val hasViolations: Boolean
            get() = dropped.isNotEmpty() || outsideAllowlist.isNotEmpty() ||
                nonGateCommits.isNotEmpty() || bypassing.isNotEmpty()

        fun render(sites: List<WriteSite>): String = sites.joinToString("\n") {
            "  $srcLabel:${it.lineIndex + 1} 于 ${it.function}() 内：`${it.raw}`"
        }

        fun renderDropped(): String = dropped.joinToString("\n") {
            "  $srcLabel:$it（归属不到函数：类体 / 初始化器）"
        }

        fun fullReport(): String = buildString {
            if (writes.isEmpty()) appendLine("  （未扫到任何写入点）")
            if (dropped.isNotEmpty()) appendLine("  [洞1] " + renderDropped())
            if (outsideAllowlist.isNotEmpty()) appendLine("  [规则1]\n" + render(outsideAllowlist))
            if (nonGateCommits.isNotEmpty()) appendLine("  [规则3] $nonGateCommits")
            if (bypassing.isNotEmpty()) appendLine("  [规则2]\n" + render(bypassing))
        }
    }

    /**
     * 扫描一份源码，判定「写入是否都过闸门」。抽成**可对内存字符串调用**的纯函数，
     * 以便自校验用例直接喂合成源码（把扫描逻辑本身也钉住）。
     */
    private fun analyzeWiring(source: String, srcLabel: String): WiringResult {
        val stripped = stripComments(source)
        val lines = stripped.split("\n")

        // 闸门判定的产物：`val X = applyPageResponse(...)` 的 X。写入值必须来自它。
        val gateVars = Regex("""\bval\s+([A-Za-z_]\w*)\s*=\s*applyPageResponse\s*\(""")
            .findAll(stripped).map { it.groupValues[1] }.toSet()

        val writes = mutableListOf<WriteSite>()
        val dropped = mutableListOf<Int>()
        for (match in WRITE_RE.findAll(stripped)) {
            val host = match.groupValues[1]
            val lineIndex = stripped.take(match.range.first).count { it == '\n' }
            val lineText = lines.getOrElse(lineIndex) { "" }
            val function = enclosingFunction(lines, lineIndex)
            if (function == null) {
                // 洞 1：不许静默丢弃。记哨兵名（必不在允许清单 → 规则 1 顶红）+ 记 dropped。
                dropped += lineIndex + 1
                writes += WriteSite(lineIndex, CLASS_BODY, null, lineText.trim())
                continue
            }
            // 洞 2：host 由整份源码匹配（可跨行）；rhs 取自**写入所在行**、行尾截断。
            // 跨行写法在 host 行取不到 `= 值` → rhs == null → 规则 2 顶红。
            val rhs = Regex("""\b${Regex.escape(host)}\s*\.\s*value\s*=\s*(.+)$""")
                .find(lineText)?.groupValues?.get(1)?.trim()
            writes += WriteSite(lineIndex, function, rhs, lineText.trim())
        }

        val outsideAllowlist = writes.filterNot { it.function in ALLOWED_FUNCTIONS }
        val nonGateCommits = (writes.map { it.function }.toSet() - GATE_EXEMPT_FUNCTIONS)
            .filterNot { functionReferencesGate(lines, it) }
        val bypassing = writes
            .filter { it.function !in GATE_EXEMPT_FUNCTIONS }
            .filter { site ->
                val rhs = site.rhs ?: return@filter true
                gateVars.none { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(rhs) }
            }

        return WiringResult(srcLabel, writes, dropped, outsideAllowlist, nonGateCommits, bypassing, gateVars)
    }

    /** 某函数的函数体是否引用闸门判定（`applyPageResponse` / `.isCurrent(`）。 */
    private fun functionReferencesGate(lines: List<String>, functionName: String): Boolean {
        val body = functionBodyOf(lines, functionName) ?: return false
        return body.contains("applyPageResponse(") || Regex("""\.isCurrent\s*\(""").containsMatchIn(body)
    }

    /** 向上（含本行）找最近的函数声明名；找不到返回 null（类体 / 初始化器）。 */
    private fun enclosingFunction(lines: List<String>, from: Int): String? {
        for (i in from downTo 0) FUN_DECL.find(lines[i])?.let { return it.groupValues[1] }
        return null
    }

    /** 用花括号配平截取某函数的函数体文本（表达式体 `= …` 也会返回 `=` 右侧文本所在片段）。 */
    private fun functionBodyOf(lines: List<String>, functionName: String): String? {
        val declLine = lines.indexOfFirst { FUN_DECL.find(it)?.groupValues?.get(1) == functionName }
        if (declLine < 0) return null
        val sb = StringBuilder()
        var depth = 0
        var started = false
        for (i in declLine until lines.size) {
            for (ch in lines[i]) {
                if (ch == '{') {
                    depth++
                    started = true
                }
                if (started) sb.append(ch)
                if (ch == '}') {
                    depth--
                    if (depth == 0) return sb.toString()
                }
            }
            if (started) sb.append('\n')
        }
        return sb.toString()
    }

    /** 找出源码里所有 `MyListPagingController` 家族（含经 `FavVideoListController` / 基类）的实现类名。 */
    private fun pagingImplementers(strippedSource: String): List<String> =
        CLASS_DECL.findAll(strippedSource)
            .filter { m -> PACING_MARKERS.any { m.groupValues[2].contains(it) } }
            .map { it.groupValues[1] }
            .toList()

    /** 去掉块注释（保留换行数以维持行号）与行注释；避免 KDoc / 注释里的字样污染扫描。 */
    private fun stripComments(source: String): String {
        val noBlock = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL).replace(source) { m ->
            "\n".repeat(m.value.count { it == '\n' })
        }
        return noBlock.lineSequence().joinToString("\n") { it.substringBefore("//") }
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

    companion object {
        private const val WIRING_SRC =
            "shared/src/commonMain/kotlin/lovehan1me/feature/library/MyListSubViewModel.kt"
        private const val LIB_DIR =
            "shared/src/commonMain/kotlin/lovehan1me/feature/library"
        private const val BASE_FILE = "MyListSubViewModel.kt"
        private const val CLASS_BODY = "<class-body>"

        /** 只钉「右值门控」的两个状态宿主（见类 KDoc 的已知边界）。 */
        private val HOSTS = listOf("itemsFlow", "mutableLoadedPageCount")

        /** 允许出现状态写入的外层函数。 */
        private val ALLOWED_FUNCTIONS = setOf("launchPage", "deleteItem", "clearMyListItems")

        /** 闸门之外的有意合法写入（删除 / 重置）——见类 KDoc 豁免理由。 */
        private val GATE_EXEMPT_FUNCTIONS = setOf("deleteItem", "clearMyListItems")

        /** 状态写入点：`host.value = …`（赋值，排除 `==`）与 `host.update { … }`；`\s*` 允许跨行。 */
        private val WRITE_RE = Regex(
            """\b(itemsFlow|mutableLoadedPageCount)\s*\.\s*(?:value\s*=(?!=)|update\b)"""
        )

        /** 函数声明：支持 `fun <T, R> name(` 这种泛型形参。 */
        private val FUN_DECL = Regex("""\bfun\s+(?:<[^>]*>\s*)?([A-Za-z_]\w*)\s*\(""")

        /** 类声明（DOTALL）：组 1 = 类名，组 2 = 到首个 `{` 为止的头部（可用于查父类型）。 */
        private val CLASS_DECL = Regex(
            """(?s)\b(?:data\s+|abstract\s+|open\s+|internal\s+|private\s+|sealed\s+)*class\s+([A-Za-z_]\w*)\b(.*?)\{"""
        )

        /** 「分页控制器」家族标记：出现任一即视为该接口的实现者。 */
        private val PACING_MARKERS = listOf(
            "MyListPagingController",
            "FavVideoListController",
            "WatchLaterListController",
            "MyListSubViewModel",
        )

        /** 本地实现者必须写死的无分页声明。 */
        private val LOCAL_LOAD_NEXT_PAGE_FALSE =
            Regex("""loadNextPage\s*\(\s*\)\s*:\s*Boolean\s*=\s*false""")

        /** 自校验：干净样本——所有写入都过闸门 / 属豁免，应无违规。 */
        private val SYNTHETIC_CLEAN = """
            class Fake {
                private val itemsFlow = MutableStateFlow(emptyList<Int>())
                private val mutableLoadedPageCount = MutableStateFlow(0)
                fun launchPage() {
                    val applied = applyPageResponse(gate, token)
                    itemsFlow.value = applied.items
                    mutableLoadedPageCount.value = applied.loadedPageCount
                }
                fun deleteItem() {
                    itemsFlow.update { it }
                }
                fun clearMyListItems() {
                    mutableLoadedPageCount.value = 0
                    itemsFlow.value = emptyList()
                }
            }
        """.trimIndent()

        /** 自校验：脏样本——在首个 `fun` 之前植入一条类体直写（洞 1 的复现形态）。 */
        private val SYNTHETIC_WITH_BYPASS = """
            class Fake {
                private val itemsFlow = MutableStateFlow(emptyList<Int>())
                private val mutableLoadedPageCount = MutableStateFlow(0)
                init { itemsFlow.value = emptyList() }
                fun launchPage() {
                    val applied = applyPageResponse(gate, token)
                    itemsFlow.value = applied.items
                    mutableLoadedPageCount.value = applied.loadedPageCount
                }
                fun deleteItem() {
                    itemsFlow.update { it }
                }
                fun clearMyListItems() {
                    mutableLoadedPageCount.value = 0
                    itemsFlow.value = emptyList()
                }
            }
        """.trimIndent()
    }
}
