plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

// compileSdk / minSdk / Java & Kotlin 目标:见根工程版本基线
android {
    namespace = "com.videobrowsing.honeysuckle.core.data"
}

dependencies {
    // 数据层:远端接口装配 + 统一信封拆解 + DTO→领域模型 + 分片合并编排。
    // :core:network 只留传输与横切能力(OkHttp 栈、指纹头、WBI、JSON/字节交换),不认识 B 站业务形状。
    //
    // 依赖一律 api:这些类型都出现在本层公开签名里
    api(project(":core:common")) // HsyResult / AppError
    api(project(":core:model")) // VideoDetail 等领域模型是 fetch 的返回类型
    api(project(":core:network")) // HsyJsonSource / HsyBytesSource 是构造入参类型
    api(project(":core:danmaku")) // DanmakuSegment / MergedDanmaku 是取数产出的产出类型
    // local 通道两条边首次接通:
    // HistoryRepository 把表与偏好盘合成一条"记进度 / 看完即清"的写路径。
    // 走 api 而不是 implementation 的理由与前述那条 DI 可见性口径同一条:
    // `@Binds` 的工厂签名里出现 HistoryDao 与 AppPreferenceRepository,:app 生成的组件要能解析到它们。
    api(project(":core:database"))
    api(project(":core:datastore"))
    // HomeFeedRepository 的返回值就是 Flow,这一条对 :app 是可见类型而非实现细节
    api(libs.kotlinx.coroutines.core)

    // 绑定与调度器槽(@Module / @Binds / @Qualifiers)在本模块,Hilt 插件仍只挂在 :app
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
