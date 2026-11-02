plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// compileSdk / minSdk / Java & Kotlin 目标:见根工程版本基线
android {
    namespace = "com.videobrowsing.honeysuckle.core.player"
}

dependencies {
    // AB 循环的总开关落在应用偏好那块盘;引擎订阅它,不再让页面替引擎记状态
    implementation(project(":core:datastore"))

    // 播放器封装 + 弹幕策略层(限帧/静默/投放速率三控独立于内核)。
    // api:DanmakuEntry 出现在 admit 的入参与产出里。
    // 刻意**不**依赖 :core:danmaku:host —— 策略层只交出"过滤后的时间轴 + 一个毫秒数",
    // 不认识 Compose;依赖边是"播放页 → 策略层 → 宿主"的单向传递。
    api(project(":core:danmaku"))

    // 播放本体(播放链的后两段)。三处 api 都是"类型跨过模块边界":
    //  · ExoPlayer 以 HsyPlayerHost.player 公开交给 PlayerView;
    //  · Playback/MediaTrack 是 PlaybackTrackSelector 与 play() 的入参与产出;
    //  · DanmakuClock 是宿主绘制阶段读的接口,实现者就是这个类。
    api(libs.media3.exoplayer)
    api(project(":core:model"))

    // DataSource 栈是 play() 内部的装配,类型不外露 → implementation。
    // 指纹头字面量只准在 :core:network 的 HsyHeaderFactory 一处,这里只把它交出去。
    implementation(libs.media3.datasource)
    // 直播 HLS(m3u8):番剧 / 视频走 DASH,直播是另一条协议族,源类型在独立产物里
    implementation(libs.media3.exoplayer.hls)
    implementation(project(":core:network"))

    // 常驻播放阶段:MediaSessionService 管前台通知与系统媒体键,
    // 播放器实例由 :app 进程级单例 PlaybackEngine 持有 —— 页面只是挂在同一实例上的渲染面。
    implementation(libs.media3.session)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
