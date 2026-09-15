package lovehan1me.ui.preview

// CMP 的类虽然由 JetBrains 发布（org.jetbrains.compose.ui:*），但包名沿用 androidx
import androidx.compose.ui.tooling.preview.Preview

/**
 * 三档窗口尺寸预览：手机 / 平板 / 桌面。
 *
 * 取值与 `ui/adaptive/Breakpoints.kt` 的断点阶梯对应——挑的是每档里最典型的一个
 * 视口：Compact(<600) 取 390×844；Medium(600~840) 取 800×1000；
 * Expanded(≥840) 取 1200×900。这样一眼能看出响应式分支有没有走对。
 *
 * 用法（替代连写三个 `@Preview`）：
 * ```
 * @PreviewSizeClasses
 * @Composable
 * private fun HomePreview() = HanimePreviewTheme { ... }
 * ```
 */
@Preview(name = "Compact", group = "尺寸", widthDp = 390, heightDp = 844)
@Preview(name = "Medium", group = "尺寸", widthDp = 800, heightDp = 1000)
@Preview(name = "Expanded", group = "尺寸", widthDp = 1200, heightDp = 900)
annotation class PreviewSizeClasses

/**
 * 组件级预览的默认档：单个手机宽度即可，用于卡片 / 按钮 / 设置行这类
 * 不关心响应式的小件（避免每个组件都渲染三遍拖慢 IDE）。
 */
@Preview(name = "组件", group = "组件", widthDp = 420)
annotation class PreviewComponent
