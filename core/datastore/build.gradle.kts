plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

// compileSdk / minSdk / Java & Kotlin 目标:见根工程版本基线
android {
    namespace = "com.videobrowsing.honeysuckle.core.datastore"
}

dependencies {
    // 设置页的数值偏好落地:DataStore 只存原始类型,
    // 密度档/限帧档这类枚举留在 core:player 策略层,不落库。
    // Flow 出现在 DanmakuPreferenceRepository 的公开签名里 → api
    api(libs.kotlinx.coroutines.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // DataStore 的类型会出现在 @Provides 的返回签名里,Hilt 在 :app 生成的组件要直接引用它 → api
    api(libs.datastore.preferences)
}
