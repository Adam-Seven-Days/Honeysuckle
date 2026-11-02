plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// compileSdk / minSdk / Java & Kotlin 目标:见根工程版本基线
android {
    namespace = "com.videobrowsing.honeysuckle.feature.live"

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    // 直播列表 / 房间状态 / 取流都经 :core:data 的 LiveRepository,屏不认识 API
    implementation(project(":core:data"))
    // 直播用自己的一只播放器(HLS/FLV),不走点播的常驻引擎 —— 直播没有续播与进度落盘
    implementation(project(":core:player"))
    implementation(libs.media3.ui)

    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
