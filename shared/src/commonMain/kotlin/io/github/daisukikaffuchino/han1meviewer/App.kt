package io.github.daisukikaffuchino.han1meviewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

/**
 * 共享 UI 的入口，三端（Android / Desktop / iOS）渲染同一份 Composable。
 *
 * 骨架阶段仅用于验证「CMP + Navigation 3 + Lifecycle ViewModel」在多平台上可编译可运行，
 * 后续各阶段的业务页面会不断替换这里的内容。
 */
sealed interface AppRoute {

    data object Home : AppRoute

    data class Detail(val code: String) : AppRoute
}

@Composable
fun App() {
    // 与现有 TopLevelBackStack 一致，用 SnapshotStateList 直接持有返回栈。
    // 需要跨进程死亡恢复时，再换成 rememberNavBackStack + SavedStateConfiguration
    // （非 JVM 平台没有反射，必须显式注册多态序列化器）。
    val backStack: SnapshotStateList<AppRoute> = remember { mutableStateListOf(AppRoute.Home) }

    MaterialTheme {
        NavDisplay(
            backStack = backStack,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider = entryProvider {
                entry<AppRoute.Home> {
                    HomeScreen(
                        onOpenDetail = { backStack.add(AppRoute.Detail(code = "hanime-0001")) },
                    )
                }
                entry<AppRoute.Detail> { route ->
                    DetailScreen(
                        code = route.code,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            },
        )
    }
}

@Composable
private fun HomeScreen(onOpenDetail: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "Han1meViewer",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "当前平台：${platformName()}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onOpenDetail) {
            Text("进入详情页")
        }
    }
}

@Composable
private fun DetailScreen(code: String, onBack: () -> Unit) {
    var opened by remember(code) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "视频 $code",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(text = "运行在 ${platformName()}")
        Button(onClick = { opened = !opened }) {
            Text(if (opened) "已展开" else "展开")
        }
        Button(onClick = onBack) {
            Text("返回")
        }
    }
}
