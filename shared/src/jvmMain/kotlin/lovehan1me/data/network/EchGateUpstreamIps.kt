package lovehan1me.data.network

import lovehan1me.core.constant.HanimeConstants

/**
 * 网关启动时交给它的上游 CF 候选 IP（种子）。
 *
 * ## 为什么必须传种子
 * 网关自己也会经 DoH 解析，但**国内 DoH 对被阻断域名会给出不可达的假 IP**——
 * 2026-10-02 Android 16KB 模拟器实测：AliDNS 把 `hanime1.me` 解析到 Facebook 段
 * （`185.60.218.50`），网关拨号 15s 超时，整页打不开；桌面端因为一直传种子
 * （探测过能建连的内置 IP）从未暴露这个问题。种子里还有网关侧探测会并入的
 * 各域 DoH 结果，**多多益善**。
 *
 * ## 取全站并集而非仅首站
 * IP 封锁常只封一批边缘 IP，姊妹站的真实边缘 IP 可能恰好可达（javchu.com 实测）。
 * 自动档拿不到（探测全挂）时退回系统解析结果，交给网关按序拨号。
 *
 * 桌面 `DesktopEchGateStarter` 与 Android `AndroidEchGateStarter` 共用这一份，
 * 避免两处各自探测；iOS 壳目前仍是空列表，属已知差异（见 docs/plan）。
 */
fun echGateSeedIps(): List<String> =
    HanimeConstants.HANIME_HOSTNAME.flatMap { host ->
        runCatching { HanimeDns.SHARED.preferredIps(host) }.getOrNull().orEmpty()
    }.distinct().ifEmpty {
        HanimeConstants.HANIME_HOSTNAME.flatMap { host ->
            runCatching { HanimeDns.SHARED.getCDNList(host) }.getOrNull().orEmpty()
        }.distinct()
    }