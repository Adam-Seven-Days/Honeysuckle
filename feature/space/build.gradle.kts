plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// compileSdk / minSdk / Java & Kotlin 目标:见根工程版本基线
android {
    namespace = "com.videobrowsing.honeysuckle.feature.space"

    buildFeatures {
        compose = true
    }
}

dependencies {
    // 空间页只读两块:名片与投稿(数据层),关注关系与登录态各一条写/订阅边
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))

    implementation(platform(libs.coil.bom))
    implementation(libs.coil.compose)

    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
