import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * 本地发布签名。
 *
 * 密钥与口令都留在开发者机器上(`keystore.properties` + `.keystore/`,两条都进 `.gitignore`),
 * 仓库里只有这段读法。没有这两个文件时 release 仍能构建,只是产出未签名包 ——
 * 让"没配密钥的机器跑不了全闸"比让"密钥进仓库"更可接受。
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile") != null

// compileSdk / minSdk / Java & Kotlin 目标:见根工程版本基线
android {
    namespace = "com.videobrowsing.honeysuckle"

    defaultConfig {
        applicationId = "com.videobrowsing.honeysuckle"
        // targetSdk 暂留 36:抬到 37 会改变运行时行为口径,属产品决策,不随 compileSdk 被动跟版
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
            )
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    // 首屏装配要读主题档,而主题档落在那块"应用偏好"盘上
    implementation(project(":core:datastore"))

    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    // hiltViewModel():把 @HiltViewModel 交进导航目的地的 ViewModelStore,
    // 转屏/返回栈重建时不会另起一份订阅 —— 用 androidx.lifecycle 那个 viewModel() 是拿不到注入的
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    // 业务屏(首版真落的那一组)
    implementation(project(":feature:home"))
    implementation(project(":feature:user"))
    implementation(project(":feature:history"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:search"))
    implementation(project(":feature:login"))
    implementation(project(":feature:videodetail"))
    implementation(project(":feature:favorite"))
    // 扩展屏:直播 / 番剧 / 动态三屏进顶层栏,闪屏盖住冷启动
    implementation(project(":feature:live"))
    implementation(project(":feature:anime"))
    implementation(project(":feature:dynamic"))
    implementation(project(":feature:splash"))

    // 首屏与"我的/历史"两条链的 Hilt 绑定都住在 :core:data(DataBindingsModule / DispatchersModule /
    // HomeFeedAssemblyModule)。它本来会经 feature 模块的 implementation 边传递进来,但组件图里
    // 有哪些 @Module 是这件工程的事,不靠传递依赖顺手带进来 —— 显式声明一次。
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    // 小窗的进度读数复用 formatClock;壳层不该为了一个格式化函数另抄一份
    implementation(project(":core:common"))

    // 弹幕渲染宿主 + 播放器装配:详情/播放页随首版就进产品导航,release 也打包
    implementation(project(":core:danmaku:host"))
    implementation(project(":core:player"))
    // 控制条与渲染面:PlayerView 在 media3-ui,不在 exoplayer 的传递依赖里
    implementation(libs.media3.ui)

    // 封面取图:图由 designsystem 的 HsyVideoCard 摆,用哪个 HTTP 客户端归这里定(见 HsyApplication)
    implementation(platform(libs.coil.bom))
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
