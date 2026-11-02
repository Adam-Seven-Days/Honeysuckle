plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// compileSdk / minSdk / Java & Kotlin 目标:见根工程版本基线
android {
    namespace = "com.videobrowsing.honeysuckle.feature.settings"

    buildFeatures {
        compose = true
    }
}

dependencies {
    // 设置页 = 两块盘的写入口:弹幕偏好落 `@DanmakuStore`,外观与"看完即清"落 `@GeneralStore`。
    // 引 :core:player 只为一件事 —— 档位词表(`DanmakuDensity` / `DanmakuFramePace`)住在那里,
    // 设置页要摆选择器就得引它。
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    // 首页档位选择器摆的是 HomeFeedSource 的词表,盘上仍落英文 id
    implementation(project(":core:model"))
    implementation(project(":core:datastore"))
    implementation(project(":core:player"))
    // 缓存管理页要读/清 cacheDir,那个口在 :core:data 的 CacheRepository
    implementation(project(":core:data"))

    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
