package lovehan1me.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * M2：三端共享的 ViewModel 获取入口。
 *
 * 背景：裸 `viewModel()` 在桌面/iOS 必崩——默认 `SavedStateViewModelFactory(nonAndroid)`
 * 调 `Factory.create(String, CreationExtras)` 直接抛 `UnsupportedOperationException`
 *（M1 桌面冒烟实测）。本项目 shared 侧 VM 除 `VideoViewModel`（M3）外均为无参构造、
 * 无 SavedStateHandle，故统一用官方 `viewModelFactory { initializer {} }` DSL
 *（builder 自行适配各平台 Factory 形状）。
 *
 * @param key 与 `viewModel(key)` 同语义（同 owner + 同 key 命中同一实例）。
 * @param create 无参构造 lambda（如 `::HomePageViewModel`）。
 */
@Composable
inline fun <reified VM : ViewModel> sharedViewModel(
    noinline create: () -> VM,
    key: String? = null,
): VM = viewModel(
    key = key,
    factory = viewModelFactory { initializer { create() } },
)
