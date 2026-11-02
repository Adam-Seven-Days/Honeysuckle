package com.videobrowsing.honeysuckle.navigation

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

/**
 * 导航过渡的口径。
 *
 * 库里不传参数时十六条路由一律走默认 fadeIn/fadeOut,而每一屏都自带不透明底色 —— 交叉淡化的中间那段
 * 两层都画在同一帧上,字互相透过去(设备实拍到的"两个页面重叠"就是这个)。所以这里把形态分成两种,
 * 按**这一趟导航的性质**选,而不是按目的地各挂一遍:
 *  - **压在栈上的二三级页**(详情 / 搜索 / 设置二级 / 直播间…):推入式。新页从右边整幅进来、旧页同步往左出去,
 *    两页始终首尾相接且全程不透明 —— 既没有叠字,也不依赖"谁画在谁上面"(导航图没开放 contentZIndex,
 *    "旧页钉住、新页盖上来"那种形态在这里做不出来,也不该假装能)。
 *  - **顶层 tab 之间**(底栏五格来回):先淡出再淡入。tab 之间没有前后关系,横向位移会暗示"还能往回滑";
 *    淡入带一段延迟是为了让上一屏先退干净,不再出现两页同时半透明。
 *
 * 两种形态都在同一处、同一个判据上分派:进入与退出必须同时是同一形态,各写各的就会出现"新页在滑、旧页在淡"。
 */
private const val SLIDE_DURATION_MS = 260

/** tab 切换:退完才进,中间不重叠 */
private const val TAB_FADE_OUT_MS = 110
private const val TAB_FADE_IN_MS = 150

/** 系统没设过动画时长缩放时的取值 */
private const val DEFAULT_ANIMATOR_SCALE = 1f

/** 位移两侧共用一份 spec:时长或缓动一旦不同,两页中间会脱开一条缝或者互相盖住 */
private val slideSpec = tween<IntOffset>(
    durationMillis = SLIDE_DURATION_MS,
    easing = FastOutSlowInEasing,
)

/**
 * 两端是不是同一层的来回?只有底栏格子之间才算。
 *
 * 判据用的是 [tabRoutes] 那份目的地表 —— 底栏有格子与这一屏算不算 tab 是同一件事,不各记一份。
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(tabRoutes: Set<String>): Boolean {
    val from = initialState.destination.route ?: return false
    val to = targetState.destination.route ?: return false
    return from in tabRoutes && to in tabRoutes
}

/** 系统把动画时长缩放关掉时,过渡整体退到零 —— 这是"可关闭动效"的兑现处 */
private fun systemMotionEnabled(context: Context): Boolean {
    val scale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        DEFAULT_ANIMATOR_SCALE,
    )
    return scale > 0f
}

internal fun hsyEnterTransition(
    tabRoutes: Set<String>,
    context: Context,
): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    when {
        !systemMotionEnabled(context) -> EnterTransition.None
        isTabSwitch(tabRoutes) -> fadeIn(tween(TAB_FADE_IN_MS, TAB_FADE_OUT_MS))
        else -> slideInHorizontally(slideSpec) { it }
    }
}

internal fun hsyExitTransition(
    tabRoutes: Set<String>,
    context: Context,
): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    when {
        !systemMotionEnabled(context) -> ExitTransition.None
        isTabSwitch(tabRoutes) -> fadeOut(tween(TAB_FADE_OUT_MS))
        else -> slideOutHorizontally(slideSpec) { -it }
    }
}

/** 返回:退回去的那一页从左边进来,压在上面的那页往右出去 —— 与进来时镜像 */
internal fun hsyPopEnterTransition(
    tabRoutes: Set<String>,
    context: Context,
): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    when {
        !systemMotionEnabled(context) -> EnterTransition.None
        isTabSwitch(tabRoutes) -> fadeIn(tween(TAB_FADE_IN_MS, TAB_FADE_OUT_MS))
        else -> slideInHorizontally(slideSpec) { -it }
    }
}

internal fun hsyPopExitTransition(
    tabRoutes: Set<String>,
    context: Context,
): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    when {
        !systemMotionEnabled(context) -> ExitTransition.None
        isTabSwitch(tabRoutes) -> fadeOut(tween(TAB_FADE_OUT_MS))
        else -> slideOutHorizontally(slideSpec) { it }
    }
}

/** 系统返回手势那两条:形态必须跟按钮返回一致,否则同一个"退"会有两种观感 */
internal fun hsyPredictivePopEnterTransition(
    tabRoutes: Set<String>,
    context: Context,
): AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition = { _ ->
    when {
        !systemMotionEnabled(context) -> EnterTransition.None
        isTabSwitch(tabRoutes) -> fadeIn(tween(TAB_FADE_IN_MS, TAB_FADE_OUT_MS))
        else -> slideInHorizontally(slideSpec) { -it }
    }
}

internal fun hsyPredictivePopExitTransition(
    tabRoutes: Set<String>,
    context: Context,
): AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> ExitTransition = { _ ->
    when {
        !systemMotionEnabled(context) -> ExitTransition.None
        isTabSwitch(tabRoutes) -> fadeOut(tween(TAB_FADE_OUT_MS))
        else -> slideOutHorizontally(slideSpec) { it }
    }
}
