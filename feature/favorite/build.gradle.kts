plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// compileSdk / minSdk / Java & Kotlin 目标:见根工程版本基线
android {
    namespace = "com.videobrowsing.honeysuckle.feature.favorite"

    buildFeatures {
        compose = true
    }
}

dependencies {
    // 收藏页两边都读:本地镜像(Room,游客也有)+ 平台收藏夹(登录才有)
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))

    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    // 夹内列表的返回键先退夹列表,再退本页
    implementation(libs.activity.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
