package lovehan1me.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 尺寸常量（**不是间距**）。
 *
 * 间距一律走 [HanimeDefaults.Spacing] —— 本文件曾经同时存在一套顶层
 * `SpacingNormal` / `SpacingLarge` / `SpacingMedium` …，与 `HanimeDefaults.Spacing`
 * 同名不同值（`SpacingMedium` 一处 8、一处 16），属于静失效的双源。审计 P0 已收敛，
 * 本文件只保留「某个控件该多宽 / 多大」这类尺寸值。
 *
 * 审计 P2：原先这里还有 `VideoNormalCardMinWidth = 145.dp` / `VideoSimplifiedCardMinWidth
 * = 95.dp`，是与响应式体系脱钩的裸值。已迁到 `ui/adaptive/PageMetrics.videoCardMinWidthFor`，
 * 按内容宽度分四档（手机档保持 145/95，平板与桌面逐档放大）。
 */
val ArtistIconSize = 72.dp
