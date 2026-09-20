package lovehan1me.app.navigation.main

import android.content.Intent
import kotlinx.serialization.json.Json
import lovehan1me.data.network.ACTION_OPEN_CLOUDFLARE_VERIFICATION
import lovehan1me.data.network.EXTRA_CLOUDFLARE_HOST
import lovehan1me.data.network.EXTRA_CLOUDFLARE_URL

// M2：navigateDrawerDestination 已下沉 shared（同名，此处删除；handleMainIntent 留守）。
// M2：CF 验证的三个 Intent 常量已随协调器一起下沉 shared（lovehan1me.data.network），
//     发起方（协调器）与接收方（本文件）共用同一份定义，避免字符串两边各写一遍。
//
// 死代码清理（2026-09-13 审阅发现）：原来这里还处理 `EXTRA_OPEN_DAILY_CHECK_IN`
// 跳签到底。但全仓（含 manifest / res/xml / 各模块）**只有读、没有任何地方写这个 extra**
// → 该分支不可达。签到页本身仍可达（设置/我的页正常入口），走的是别的路由，
// 所以连同常量一起删除，不是"删功能"。

fun TopLevelBackStack<HanimeScreen>.handleMainIntent(intent: Intent) {
    if (intent.action == ACTION_OPEN_CLOUDFLARE_VERIFICATION) {
        val url = intent.getStringExtra(EXTRA_CLOUDFLARE_URL)
        val host = intent.getStringExtra(EXTRA_CLOUDFLARE_HOST)
        intent.removeExtra(EXTRA_CLOUDFLARE_URL)
        intent.removeExtra(EXTRA_CLOUDFLARE_HOST)
        intent.action = null
        if (!url.isNullOrBlank() && !host.isNullOrBlank()) {
            add(CloudflareRoute(url = url, host = host), launchSingleTop = true)
        }
        return
    }

    intent.data?.let { uri ->
        when (uri.scheme) {
            "http", "https" -> {
                val videoCode = uri.getQueryParameter("v")
                if (videoCode != null) {
                    add(VideoRoute(videoCode))
                    return
                }
            }

            "file", "content" -> {
                add(VideoRoute("-1", uri.toString()))
                return
            }
        }
    }

    intent.getStringExtra("startSearchFromTag")?.let { tag ->
        intent.removeExtra("startSearchFromTag")
        add(SearchRoute(query = tag))
        return
    }

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    val map = intent.getSerializableExtra("startSearchFromMap") as? HashMap<String, String>
    if (map != null) {
        intent.removeExtra("startSearchFromMap")
        add(SearchRoute(advancedSearchJson = Json.encodeToString(map)))
        return
    }

    val videoCode = intent.getStringExtra("startVideoCode")
    if (!videoCode.isNullOrEmpty()) {
        intent.removeExtra("startVideoCode")
        add(VideoRoute(videoCode))
    }
}
