plugins {
    alias(libs.plugins.android.library)
}

// compileSdk / minSdk / Java & Kotlin 目标:见根工程版本基线
android {
    namespace = "com.videobrowsing.honeysuckle.core.network"
}

dependencies {
    // 网络基础块:OkHttp WEB 通道 + 指纹头拦截器 + JSON/字节交换层(API Service 的 Retrofit 化尚未接入)
    //
    // 本层不认识 B 站业务形状:接口装配、信封拆解、DTO→领域模型、分片合并都在 `:core:data`。
    //
    // 依赖用 api 而非 implementation:本层的公开签名就直接暴露这些类型
    // (getJson 返回 JsonElement、getBytes 返回 HsyResult、urlFor 相关类型来自 OkHttp),
    // 用 implementation 会让调用方"编译期看不到返回值类型"。
    api(project(":core:common"))
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)
}
