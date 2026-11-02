plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// compileSdk / minSdk / Java & Kotlin 目标:见根工程版本基线
android {
    namespace = "com.videobrowsing.honeysuckle.feature.user"

    buildFeatures {
        compose = true
    }
}

dependencies {
    // "我的"页只消费本地那两条链(历史表 + 偏好盘),不碰远端接口 —— 这一屏断网也能全开。
    // designsystem 的 compose 面由它的 api 边带入,这里不重复声明 compose-ui/foundation。
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))

    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // 账号头像走 Coil;网络栈由 :app 的那只 ImageLoader 提供,这里只要 compose 面
    implementation(platform(libs.coil.bom))
    implementation(libs.coil.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
