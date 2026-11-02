package com.videobrowsing.honeysuckle.feature.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import com.videobrowsing.honeysuckle.core.designsystem.ui.Text
import kotlinx.coroutines.delay

/**
 * 冷启动闪屏。
 *
 * 只做一件事:给"打开应用"一个安静的落点,顺便盖住导航图首帧的取数骨架。
 * 不接推荐位、不接开屏广告;时长是一屏呼吸的长度,
 * 不做"必须看完"的倒计时,也不给跳过按钮 —— 它没有要你看的内容,跳过没有意义。
 */
@Composable
fun SplashScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = FADE_IN_MS),
        label = "splash-alpha",
    )

    LaunchedEffect(Unit) {
        visible = true
        // 冷启动时首帧可能要几秒才画出来:计时必须从"这一屏真的上屏"之后开始,
        // 否则 delay 会在首帧之前就走完,闪屏一帧都看不到。
        withFrameNanos { }
        withFrameNanos { }
        delay(HOLD_MS)
        onDone()
    }

    Box(
        modifier = modifier.fillMaxSize().background(Hsy.colors.primary),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.alpha(alpha).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "金银花",
                style = Hsy.typography.displaySmall,
                fontSize = TITLE_SIZE,
                color = Hsy.colors.onPrimary,
            )
            Text(
                text = "干净地看视频",
                style = Hsy.typography.bodyLarge,
                color = Hsy.colors.onPrimary.copy(alpha = SUBTITLE_ALPHA),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val FADE_IN_MS = 220
private const val HOLD_MS = 700L
private const val SUBTITLE_ALPHA = 0.82f

// 闪屏标题比正文大得多,用独立的字体大小而不是借 typography 里最小那档再乘系数
private val TITLE_SIZE = 44.sp
