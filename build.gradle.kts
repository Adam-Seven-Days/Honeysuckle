import com.android.build.api.dsl.CommonExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

// 根工程:登记插件版本 + 全仓版本基线的唯一声明处;模块脚本不再重复这些数字
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

// 构建需 JDK 21;Java/Kotlin 产物字节码统一 21。
// class file 65 由 AGP 9.4.1 的 D8/R8 直接 dex,无需压回 17。
// toolchain 让编译 JVM 锁定 21,不再受守护进程 JAVA_HOME 影响。
val jdkVersion = 21
val compileSdkVersion = 37
val minSdkVersion = 26
val javaBaseline = JavaVersion.VERSION_21
val jvmBaseline = JvmTarget.JVM_21

subprojects {
    afterEvaluate {
        extensions.findByType(CommonExtension::class.java)?.apply {
            compileSdk = compileSdkVersion
            defaultConfig.minSdk = minSdkVersion
            compileOptions.sourceCompatibility = javaBaseline
            compileOptions.targetCompatibility = javaBaseline
        }

        if (pluginManager.hasPlugin("org.jetbrains.kotlin.jvm")) {
            extensions.findByType(KotlinJvmProjectExtension::class.java)?.apply {
                jvmToolchain(jdkVersion)
                compilerOptions.jvmTarget.set(jvmBaseline)
            }
            extensions.findByType(JavaPluginExtension::class.java)?.apply {
                sourceCompatibility = javaBaseline
                targetCompatibility = javaBaseline
            }
        }

        if (pluginManager.hasPlugin("com.android.application") || pluginManager.hasPlugin("com.android.library")) {
            extensions.findByType(KotlinAndroidProjectExtension::class.java)?.apply {
                jvmToolchain(jdkVersion)
                compilerOptions.jvmTarget.set(jvmBaseline)
            }
            // Hilt 生成的 hiltJavaCompile<Variant> 是裸 JavaCompile 任务:它不继承 AGP/KGP 的 toolchain,
            // 退回守护进程 JVM(本机 17)去编 source 21,报"无效的源发行版:21"。
            // 这里给 Android 模块的全部 JavaCompile 显式钉 21 launcher,基线才对"Kotlin 之外"的编译同样成立。
            extensions.findByType(JavaToolchainService::class.java)?.let { toolchains ->
                val compiler = toolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(jdkVersion)) }
                tasks.withType(JavaCompile::class.java).configureEach { javaCompiler.set(compiler) }
            }
        }
    }
}

// ---- 静态检查:格式卡口----
// 规则口径只写在根 .editorconfig 一处;引擎版本钉在版本目录,模块脚本不重复声明。
// 代码风格由 .editorconfig 的 ktlint_code_style 决定,插件的 android 开关不设置。
val ktlintEngineVersion = libs.versions.ktlintEngine.get()

subprojects {
    afterEvaluate {
        val hasKotlin = pluginManager.hasPlugin("org.jetbrains.kotlin.jvm") ||
            pluginManager.hasPlugin("com.android.application") ||
            pluginManager.hasPlugin("com.android.library")
        if (hasKotlin) {
            apply(plugin = "org.jlleitschuh.gradle.ktlint")
            apply(plugin = "io.gitlab.arturbosch.detekt")
            extensions.configure(KtlintExtension::class.java) {
                version.set(ktlintEngineVersion)
                ignoreFailures.set(false)
            }
            extensions.configure(DetektExtension::class.java) {
                // detekt 1.23.8 的扩展属性是普通 var,不是 Gradle Property,不能用 .set();
                // 且它没有 toolVersion——插件版本即分析引擎版本,已由版本目录的 detekt 钉住。
                buildUponDefaultConfig = true
                ignoreFailures = false
                config.setFrom(rootProject.file("detekt/detekt.yml"))
            }
        }
    }
}
