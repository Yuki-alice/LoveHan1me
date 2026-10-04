package lovehan1me.data.network.egress

/**
 * 诊断导出：把近端事件流渲染成可粘贴的文本（设置页"复制"按钮用）。
 *
 * 纯函数、无平台相关；字段全部 ASCII 标识（域/路由/结局），不做本地化 ——
 * 诊断文本是给开发者看的，各语言设置下同一份格式才能对着复现。
 */
fun buildEgressExport(events: List<EgressEvent>): String = buildString {
    appendLine("LoveHan1me egress diagnostics (${events.size} events)")
    events.forEach { event ->
        appendLine(
            "${event.atMs} ${event.domain} ${event.route} ${event.outcome} " +
                "rtt=${event.rttMs}ms budget=${event.budgetMs}ms",
        )
    }
}
