package com.videobrowsing.honeysuckle.core.designsystem.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.videobrowsing.honeysuckle.core.designsystem.theme.Hsy
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

/** 下拉的阻尼:手指走两格,内容走一格,松手才有"拉回"的手感 */
private const val PULL_DAMPING = 0.5f

/**
 * 触发刷新要过的位移:配上 [PULL_DAMPING],手指得实打实拖过两倍距离才够。
 * 稍微一动就冒图标,是把"滑了一下"当成"要刷新"——这一档把两者分开。
 */
private val PULL_TRIGGER_DISTANCE = 88.dp

/** 够到触发位之后还能再拽的余量;再多只让缝更大,不让"刷新"这件事变得更确定 */
private val PULL_MAX_DISTANCE = 112.dp

/** 图标从触发位的这一成开始渐显,到触发位刚好全亮;往回拖是同一条坡,于是变成渐隐 */
private const val INDICATOR_FADE_START = 0.45f

/**
 * 已经够到触发位之后,图标淡到这一成就改判为"放弃刷新"。
 *
 * 迟滞不是可有可无的:贴着触发位来回抖两下,没有这条余量会让"要不要刷新"在两帧之间换答案,
 * 而淡到一半以下的图标本身就在说"这一把不算了"——视觉与判据说的是同一件事。
 */
private const val INDICATOR_CANCEL_ALPHA = 0.5f

/** 松手后收回那一段的时长 */
private const val PULL_SETTLE_MS = 220

/** 松手够触发时停在触发位那一段的时长 */
private const val PULL_HOLD_MS = 150

/** 触发之后等 isRefreshing 翻牌的最长时间:超时就当这一把没换来真刷新,自己收回 */
private const val PULL_ACK_TIMEOUT_MS = 600L

/** 位移到图标透明度:没进渐显段是 0,到触发位是 1,中间线性 */
private fun indicatorAlpha(pullPx: Float, triggerPx: Float): Float =
    ((pullPx / triggerPx - INDICATOR_FADE_START) / (1f - INDICATOR_FADE_START)).coerceIn(0f, 1f)

/** 拖拽途中重算"够不够触发":进触发位才算数,淡到 [INDICATOR_CANCEL_ALPHA] 以下才反悔 */
private fun armedAfterDrag(armed: Boolean, pullPx: Float, triggerPx: Float): Boolean {
    val alpha = indicatorAlpha(pullPx, triggerPx)
    return if (armed) alpha >= INDICATOR_CANCEL_ALPHA else alpha >= 1f
}

/**
 * 一次下拉的全部读数。
 *
 * 账落在 [pullPx] 上(拖到哪),而 [offset] 只负责画到哪:判据不该去读动画的中间值 ——
 * 慢速松手那一刻动画还在半路,拿中间值算会错这一把到底够不够触发。
 */
@Stable
private class PullState {
    val offset = Animatable(0f)
    var pullPx by mutableFloatStateOf(0f)
        private set
    var armed by mutableStateOf(false)
        private set

    /** 手指还在不在屏上:落位要靠它躲开"用户已经起手下一把"的那种重叠 */
    var dragging by mutableStateOf(false)

    /** 抬了几次手。指针作用域里只许调指针 API,落位的动画都在组合侧按这一笔重跑 */
    var liftSeq by mutableIntStateOf(0)
        private set

    /**
     * 手指往下走的那一段记进账里;返回 false 表示这段与下拉无关,列表自己吃掉就好。
     *
     * 只有列表在顶、这段下拉它接不住时才轮得到我们 —— 列表滚到中间不会误触,道理就在这。
     */
    fun pullBy(deltaY: Float, triggerPx: Float, maxPullPx: Float): Boolean {
        if (deltaY <= 0f) return false
        apply((pullPx + deltaY * PULL_DAMPING).coerceAtMost(maxPullPx), triggerPx)
        return true
    }

    /**
     * 往上推:先把已经拉出来的那段收回去,整段位移由我们吃掉,列表在这一截拿不到手指。
     *
     * 必须抢在 `onPreScroll` 里做:列表在前滚方向上本来就能动,往上一推它全吃了,
     * 尾随事件里根本没有剩余 —— 那样"上滑放弃刷新"永远成立不了。
     * 收回去带同样的阻尼:拖出来要走的路,往回也得走那么远,手感才对得上。
     */
    fun retract(deltaY: Float, triggerPx: Float): Boolean {
        if (deltaY >= 0f || pullPx <= 0f) return false
        apply((pullPx + deltaY * PULL_DAMPING).coerceAtLeast(0f), triggerPx)
        return true
    }

    private fun apply(next: Float, triggerPx: Float) {
        pullPx = next
        armed = armedAfterDrag(armed, next, triggerPx)
    }

    /** 落位:把账定在 [value] 上;armed 跟着清掉 —— 这一把要么已经交出去,要么不算了 */
    fun settleTo(value: Float) {
        pullPx = value
        armed = false
    }

    /** 记一笔"手指全抬起来了" */
    fun onLift() {
        liftSeq++
    }
}

/**
 * 下拉刷新:内容跟着手指下移,图标按位移渐显,松手过阈值触发 [onRefresh],转圈到 [isRefreshing] 变回 false。
 *
 * 用嵌套滚动接住列表没吃掉的那段下拉,所以列表滚到中间时不会误触。
 * 四条屏上口:
 *  - **稍微一拉不出图标**:渐显段之前透明度是 0,别让用户以为手一碰就要刷新;
 *  - **拖回来就是放弃**:够触发之后往上一推,这一截位移归收回下拉,列表暂时不动;
 *    图标沿同一条坡渐隐,淡过一半那刻判据跟着改回"不刷新";
 *  - **慢慢松手也落得了位**:落位盯的是手指抬起这一刻,不是 fling —— 慢速松手不带惯性,
 *    列表根本不会派发 fling,只靠 `onPreFling` 判会把图标停在半路;
 *  - **没换来真刷新的那一把不留转圈**:调用方接不住时(游客在"关注"档下拉只是重新问一次登录态),
 *    等牌超时就自己收回,不让图标一个人钉在屏上。
 */
@Composable
fun PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val triggerPx = with(density) { PULL_TRIGGER_DISTANCE.toPx() }
    val maxPullPx = with(density) { PULL_MAX_DISTANCE.toPx() }
    val pull = remember { PullState() }
    val connection = rememberPullConnection(pull, triggerPx, maxPullPx, isRefreshing)
    PullSettleEffects(pull, triggerPx, isRefreshing, onRefresh)

    Box(
        modifier = modifier
            .nestedScroll(connection)
            .pointerInput(Unit) { awaitLifts(pull) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, pull.offset.value.roundToInt()) },
        ) {
            content()
        }
        // 透明度跟着画出来的那一份走,渐显与渐隐才和位移是同一件事
        val alpha = indicatorAlpha(pull.offset.value, triggerPx)
        if (alpha > 0f) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .alpha(alpha)
                    .padding(top = 14.dp)
                    .size(22.dp),
                color = Hsy.colors.accent,
            )
        }
    }
}

/** 接住列表没吃掉的那段下拉:位移记进 [pull] 的账,画出来的那一份跟着它落位 */
@Composable
private fun rememberPullConnection(
    pull: PullState,
    triggerPx: Float,
    maxPullPx: Float,
    isRefreshing: Boolean,
): NestedScrollConnection {
    val drawScope = rememberCoroutineScope()
    val refreshing by rememberUpdatedState(isRefreshing)
    return remember(triggerPx, maxPullPx) {
        fun redraw() {
            drawScope.launch { pull.offset.snapTo(pull.pullPx) }
        }
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 往上推先抢在列表之前把下拉收回去,整段吃掉:这一截列表不动,图标沿同一条坡渐隐
                if (source != NestedScrollSource.Drag || refreshing) return Offset.Zero
                if (!pull.retract(available.y, triggerPx)) return Offset.Zero
                redraw()
                return Offset(0f, available.y)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // 只接手指:列表自己的惯性不在这条链上,不该被折成下拉
                if (source != NestedScrollSource.Drag || refreshing) return Offset.Zero
                if (!pull.pullBy(available.y, triggerPx, maxPullPx)) return Offset.Zero
                redraw()
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // 下拉还没收回时不把惯性交给列表:否则圈还在转,内容已经滚走了
                return if (pull.pullPx > 0f) available else Velocity.Zero
            }
        }
    }
}

/** 两处落位:刷新办完收回一次,手指抬起再定一次 */
@Composable
private fun PullSettleEffects(pull: PullState, triggerPx: Float, isRefreshing: Boolean, onRefresh: () -> Unit) {
    val refreshing by rememberUpdatedState(isRefreshing)
    val requestRefresh by rememberUpdatedState(onRefresh)

    suspend fun retract() {
        pull.settleTo(0f)
        pull.offset.animateTo(0f, tween(PULL_SETTLE_MS))
    }

    // 这一段不跟手指,是"事情办完了"的收尾
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && pull.pullPx > 0f) retract()
    }

    // 抬起这一刻:够触发就交出去并停在触发位等牌,不够就沿同一条坡收回去
    LaunchedEffect(pull.liftSeq) {
        if (pull.liftSeq == 0 || pull.dragging) return@LaunchedEffect
        if (refreshing || pull.pullPx <= 0f) return@LaunchedEffect
        if (!pull.armed) {
            retract()
            return@LaunchedEffect
        }
        pull.settleTo(triggerPx)
        requestRefresh()
        pull.offset.animateTo(triggerPx, tween(PULL_HOLD_MS))
        // 等一张真刷新的牌:接不住的那种下拉不该把转圈一个人钉在屏上
        val acked = withTimeoutOrNull(PULL_ACK_TIMEOUT_MS) { snapshotFlow { refreshing }.first { it } } != null
        if (!acked && !pull.dragging) retract()
    }
}

/** 盯手指抬起这一刻:Initial pass 只观察、不吃事件,点击与滚动照常交给子节点 */
private suspend fun PointerInputScope.awaitLifts(pull: PullState) {
    awaitEachGesture {
        var pressed = false
        var lifted = false
        while (!lifted) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.any { it.pressed }) {
                pressed = true
                pull.dragging = true
            }
            lifted = pressed && event.changes.none { it.pressed }
        }
        pull.dragging = false
        pull.onLift()
    }
}
