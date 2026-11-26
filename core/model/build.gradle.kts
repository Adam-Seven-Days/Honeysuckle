plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(kotlin("test-junit"))
}

// 纯 Kotlin 模块,零 Android 依赖;Java/Kotlin 目标由根工程基线统一给出
