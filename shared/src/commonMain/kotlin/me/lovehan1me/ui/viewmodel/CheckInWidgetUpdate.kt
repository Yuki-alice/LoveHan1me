package me.lovehan1me.ui.viewmodel

/**
 * P6d-2：打卡小组件刷新（替代 Glance `CheckInWidget().updateAll(application)`）。
 *
 * - androidMain：TODO P7——Glance widget 本体留 :app（P7），shared 无法引用 :app 的
 *   CheckInWidget 类，暂 no-op。provider 侧自行读库，影响仅为增删后小组件非即时刷新。
 * - desktopMain / iosMain：no-op（无小组件）。
 */
expect fun updateCheckInWidget()
