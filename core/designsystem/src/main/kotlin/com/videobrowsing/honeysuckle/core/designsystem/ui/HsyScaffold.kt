package com.videobrowsing.honeysuckle.core.designsystem.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 下拉的阻尼:手指走两格,内容走一格,松手才有"拉回"的手感 */
private const val PULL_DAMPING = 0.5f

/**
 * 极简脚手架:内容在上、底栏在下。
 *
 * 没有 `innerPadding` 的复杂语义:这一仓的壳层在 Activity 上用 `safeDrawingPadding`
 * 处理系统栏,底栏在 Column 里真实占位,内容自然拿到剩下的高度。
 */
@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content(PaddingValues.Zero)
        }
        bottomBar()
    }
}

/**
 * 下拉刷新:内容跟着手指下移,松手过阈值触发 [onRefresh],转圈直到 [isRefreshing] 变回 false。
 *
 * 用嵌套滚动接住列表没吃掉的那段下拉,所以列表滚到中间时不会误触。
 */
@Composable
fun PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val threshold = with(density) { 64.dp.toPx() }
    val maxPull = with(density) { 96.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val currentRefreshing by rememberUpdatedState(isRefreshing)

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) offset.animateTo(0f, tween(durationMillis = 200))
    }

    val connection = remember(threshold, maxPull) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && !currentRefreshing) {
                    scope.launch {
                        offset.snapTo((offset.value + available.y * PULL_DAMPING).coerceIn(0f, maxPull))
                    }
                    return Offset(0f, available.y)
                }
                if (available.y < 0f && offset.value > 0f) {
                    scope.launch {
                        offset.snapTo((offset.value + available.y).coerceAtLeast(0f))
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset.value >= threshold && !currentRefreshing) {
                    currentOnRefresh()
                    offset.animateTo(threshold, tween(durationMillis = 150))
                    return available
                }
                if (offset.value > 0f) {
                    offset.animateTo(0f, tween(durationMillis = 200))
                }
                return Velocity.Zero
            }
        }
    }

    Box(modifier = modifier.nestedScroll(connection)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, offset.value.roundToInt()) },
        ) {
            content()
        }
        if (offset.value > 0f) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
                    .size(22.dp),
                color = Hsy.colors.accent,
            )
        }
    }
}
