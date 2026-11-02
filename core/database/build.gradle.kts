plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

// compileSdk / minSdk / Java & Kotlin 目标:见根工程版本基线
android {
    namespace = "com.videobrowsing.honeysuckle.core.database"
}

dependencies {
    // 历史记录落 Room:表结构、DAO、实体 ↔ 领域模型转换都在本模块,UI 只见领域模型。
    // room-ktx 不再挂:2.7 起它的 API 已并进 room-runtime,单独引只是拖一份旧的 coroutines 约束。
    api(project(":core:model")) // HistoryEntry 是 toDomain() 的返回类型
    api(libs.kotlinx.coroutines.core) // DAO 返回 Flow
    api(libs.room.runtime) // @Database/@Dao 出现在公开声明上,Hilt 生成的组件也要能解析这些类型

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.room.compiler)
}
ksp {
    // 建表 SQL 落成 JSON:表结构的凭据
    arg("room.schemaLocation", "$projectDir/schemas")
}
