plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// compileSdk / minSdk / Java & Kotlin 目标:见根工程版本基线
android {
    namespace = "com.videobrowsing.honeysuckle.feature.anime"

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))

    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // 同系列卡片要摆封面;底图统一走 Coil,与首页/详情页同一套缓存
    implementation(platform(libs.coil.bom))
    implementation(libs.coil.compose)
}
