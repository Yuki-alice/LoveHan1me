package lovehan1me.data.network.egress

import lovehan1me.core.constant.HanimeConstants
import lovehan1me.core.domain.model.ProxyMode

/**
 * 代理档位的作用范围（F8 规则可见）。
 *
 * 三端共用同一份判据，UI 只负责渲染 —— 与既有约定一致（判定不进平台分叉）。
 */
enum class ProxyRuleScope {
    /** 全部流量按代理设置走（历史行为）。 */
    All,

    /** 仅受限域走代理，第三方恒直连。 */
    RestrictedOnly,
}

/**
 * 当前代理档位的规则摘要。
 *
 * @property scope 作用范围。
 * @property hosts [ProxyRuleScope.RestrictedOnly] 下会走代理的已知域（内置受限域 + 当前站点，
 *   已去重、保持顺序）；其余两档为空 —— 那两档的语义是"全部"或"全不"，逐域列举没有意义。
 * @property shadowedBy 被哪一项「手动强制选路」盖住（非空 = 本项当前不生效）。
 */
data class ProxyRuleSummary(
    val scope: ProxyRuleScope,
    val hosts: List<String> = emptyList(),
    val shadowedBy: ForceMode? = null,
)

/**
 * 由「代理档位 + 手动强制选路 + 当前站点」推出规则摘要。
 *
 * **为什么把 [ForceMode] 也吃进来**：§6.2 记录了三个出口开关（[ProxyMode] / [ForceMode] /
 * 规划中的每域覆盖）互不感知的历史问题。在优先级定死之前，至少要让用户**看见**谁盖住了谁 ——
 * 静默失效会让用户报"我明明开了直连却还在走代理"，而三份代码各自都没写错。
 *
 * 这里的判定因此只做一件事：诚实地标出"本项当前被盖住"，**不改变任何一方的行为**。
 *
 * @param siteHost 当前站点域名（自定义镜像不在内置表里，但必须与站点同出口）。
 */
fun proxyRuleSummary(
    mode: ProxyMode,
    force: ForceMode,
    siteHost: String?,
): ProxyRuleSummary {
    val scope = if (mode == ProxyMode.Rules) ProxyRuleScope.RestrictedOnly else ProxyRuleScope.All
    val hosts = if (scope == ProxyRuleScope.RestrictedOnly) {
        // 与 jvmMain `HanimeProxySelector.restrictedForProxy` 同口径：内置受限域 + 当前站点。
        // 判据本体是 [isRestrictedHost]；这里只把它"展开"成人能读的清单。
        buildList {
            HanimeConstants.HANIME_HOSTNAME.forEach { add(it) }
            add(GETCHU_HOST)
            if (!siteHost.isNullOrBlank() && !contains(siteHost)) add(siteHost)
        }.distinct()
    } else {
        emptyList()
    }
    return ProxyRuleSummary(scope = scope, hosts = hosts, shadowedBy = shadowingForceMode(force))
}

/**
 * 代理档位是否被 [force] 盖住。
 *
 * 只标**语义明确**的组合，宁可漏标也不误标 —— 误标会让用户以为设置坏了，而其实没坏：
 * - `ForceDirect` / `ForceGate`：调度器分别强制直连与强制走网关，**代理不参与**，
 *   代理档位无论选什么都无从生效 ⇒ 标出来；
 * - `ForceProxy`：强制走代理。它与"仅受限域"的叠加结果是"调度器要代理、而选择器对
 *   第三方不给代理"，最终行为取决于调度器对"无代理可用"的回退 —— **这一层尚未厘清**，
 *   属 §6.2 待定的优先级范围。在定论之前不标，免得给出一句不准确的"暂不生效"。
 */
internal fun shadowingForceMode(force: ForceMode): ForceMode? = when (force) {
    ForceMode.Auto, ForceMode.ForceProxy -> null
    ForceMode.ForceDirect, ForceMode.ForceGate -> force
}
