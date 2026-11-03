plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// compileSdk / minSdk / Java & Kotlin 目标:见根工程版本基线
android {
    namespace = "com.videobrowsing.honeysuckle.feature.message"

    buildFeatures {
        compose = true
    }
}

dependencies {
    // 消息页只读:三栏提醒 + 未读读数;登录态从仓库订阅,头像走 Coil
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
