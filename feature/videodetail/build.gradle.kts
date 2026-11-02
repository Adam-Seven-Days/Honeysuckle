plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// compileSdk / minSdk / Java & Kotlin 目标:见根工程版本基线
android {
    namespace = "com.videobrowsing.honeysuckle.feature.videodetail"

    buildFeatures {
        compose = true
    }
}

dependencies {
    // 这一屏是产品的汇合点:详情 / 相关推荐 / 取流 / 弹幕 / 历史 / 互动 / 登录态,全部经数据层接口拿。
    // 播放器与弹幕宿主是它第一次进产品导航后要同时装配的两件。
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))
    implementation(project(":core:player"))
    implementation(project(":core:danmaku:host"))

    // 渲染面(PlayerView)在 media3-ui;exoplayer 由 :core:player 的 api 边带入
    implementation(libs.media3.ui)
    implementation(platform(libs.coil.bom))
    implementation(libs.coil.compose)

    // BackHandler:全屏时返回键先退全屏,再回上一页
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
