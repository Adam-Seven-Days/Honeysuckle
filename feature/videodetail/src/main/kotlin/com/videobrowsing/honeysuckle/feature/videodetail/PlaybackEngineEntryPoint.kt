package com.videobrowsing.honeysuckle.feature.videodetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.videobrowsing.honeysuckle.core.player.PlaybackEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 播放引擎是进程级单例,不该为了拿它给 ViewModel 找一个页面作用域的座位 ——
 * 它比任何一屏活得久,注进 ViewModel 只会让人误以为会随页面销毁。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PlaybackEngineEntryPoint {
    fun playbackEngine(): PlaybackEngine
}

@Composable
internal fun rememberPlaybackEngine(): PlaybackEngine {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors.fromApplication(context, PlaybackEngineEntryPoint::class.java).playbackEngine()
    }
}
