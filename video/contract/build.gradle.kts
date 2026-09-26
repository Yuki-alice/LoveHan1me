// :video:contract —— 视频模块的契约层（纯 Kotlin）。
//
// 硬边界：**不得依赖 Compose、不得依赖 mediamp、不得依赖任何平台 API**。
// 违反任一条，类型会漏进 :shared 的 iOS framework export，也会让引擎实现反过来被 UI 依赖。
// 本模块只放：播放领域的类型（命令 / 状态 / 事件 / 能力）与纯派生函数。

plugins {
    id("han1me-kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // StateFlow / SharedFlow 出现在公开 API 签名里，必须 api 导出，
            // 否则 :video:engine 与 :shared 编译期看不到这两个类型。
            api(libs.coroutines.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
