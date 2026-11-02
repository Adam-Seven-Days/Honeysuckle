plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

// compileSdk / minSdk / Java & Kotlin 目标:见根工程版本基线
android {
    namespace = "com.videobrowsing.honeysuckle.core.designsystem"

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)

    // 封面的取图与淡入归组件本身(HsyVideoCard 要用),但用哪个 OkHttpClient 不归这里:
    // 图片走的客户端由 :app 在 HsyApplication 上装配成 WEB 通道那一份,设计层不替调用方决定网络口径。
    implementation(platform(libs.coil.bom))
    implementation(libs.coil.compose)
}
