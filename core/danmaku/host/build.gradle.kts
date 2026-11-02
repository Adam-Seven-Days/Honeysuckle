// 弹幕渲染宿主(Compose 侧):内核算坐标,这里只做"量字模 + 画像素"
// 独立成模块是为了保住 :core:danmaku 的纯 Kotlin 性质。
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

// compileSdk / minSdk / Java & Kotlin 目标:见根工程版本基线
android {
    namespace = "com.videobrowsing.honeysuckle.core.danmaku.host"

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":core:danmaku"))

    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    implementation(libs.compose.foundation)
}
