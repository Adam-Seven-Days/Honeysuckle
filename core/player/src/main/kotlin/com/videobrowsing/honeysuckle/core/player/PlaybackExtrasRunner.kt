package com.videobrowsing.honeysuckle.core.player

/**
 * AB 循环与定时关闭的执行体。
 *
 * 它是纯 Kotlin:**时钟与播放头都由调用方递进来**,所以"到点回跳""定时到点该暂停"
 * 这类判断在 JVM 里就能全量断言 —— 而这两件事恰恰是最不该在设备上试错的:
 * 一次误判就是无限 seek 或提前掐掉用户正在看的内容。
 *
 * [PlaybackEngine] 每 [PlaybackEngine.ENGINE_TICK_MS] 递一拍,页面在不在前台都照跑,
 * 因为"页面已经没了但循环还在切"正是常驻化的意义。
 */
class PlaybackExtrasRunner(private val minAbSpanMs: Long = DEFAULT_MIN_AB_SPAN_MS) {

    /** 屏上读数那一份快照;引擎把它原样交给 StateFlow */
    var state: PlaybackExtras = PlaybackExtras()
        private set

    /** 本拍要引擎执行的动作 —— 引擎认识播放器,这里只认识"该做什么" */
    sealed interface TickAction {
        data object None : TickAction
        data class SeekTo(val positionMs: Long) : TickAction
        data object Pause : TickAction
    }

    private var sleepDeadlineElapsedMs: Long? = null

    /**
     * AB 总开关。设置页写盘、引擎订阅后转到这里;关掉的一瞬间要把旧点一起清掉 ——
     * 再打开时若还留着上一段视频的 A/B,用户会以为按钮坏了。
     */
    private var abLoopEnabled = false

    /** 设置页的 AB 开关;关掉时清点,打开时只改开关不动已有点 */
    fun setAbLoopEnabled(enabled: Boolean) {
        abLoopEnabled = enabled
        state = if (enabled) {
            state.copy(abLoopEnabled = true)
        } else {
            state.copy(abLoopEnabled = false, abStartMs = null, abEndMs = null)
        }
    }

    /** AB:第一次标 A、第二次标 B、第三次清空;第二次落在 A 之后不足 [minAbSpanMs] 时当成撤回 A */
    fun markAbPoint(positionMs: Long) {
        if (!abLoopEnabled) return
        val current = state
        val start = current.abStartMs
        state = when {
            start == null -> current.copy(abStartMs = positionMs)

            current.abEndMs == null ->
                if (positionMs > start + minAbSpanMs) {
                    current.copy(abEndMs = positionMs)
                } else {
                    current.copy(abStartMs = null, abEndMs = null)
                }

            else -> current.copy(abStartMs = null, abEndMs = null)
        }
    }

    /** 换视频/换分 P:AB 是"这一条流的两个时刻",旧点没有意义;定时关闭与内容无关,留着 */
    fun clearAbPoints() {
        state = state.copy(abStartMs = null, abEndMs = null)
    }

    /** 定时关闭:分钟数;null 或非正数表示取消。[nowElapsedMs] 用单调时钟(`SystemClock.elapsedRealtime`) */
    fun setSleepTimer(minutes: Int?, nowElapsedMs: Long) {
        if (minutes == null || minutes <= 0) {
            sleepDeadlineElapsedMs = null
            state = state.copy(sleepRemainingMs = null)
            return
        }
        val totalMs = minutes.toLong() * MS_PER_MINUTE
        sleepDeadlineElapsedMs = nowElapsedMs + totalMs
        state = state.copy(sleepRemainingMs = totalMs)
    }

    /** 会话结束(用户明确关掉播放):两件事一起清;AB 总开关跟设置走,不随会话归零 */
    fun reset() {
        sleepDeadlineElapsedMs = null
        state = PlaybackExtras(abLoopEnabled = abLoopEnabled)
    }

    /**
     * 一拍。
     *
     * 定时读数**只在整秒变化时**才重发:200ms 一拍意味着一秒会刷五次同一个数,
     * 那五次重组对屏上没有任何意义。
     *
     * 两件事在同一拍撞定时时,**定时优先**且这一拍不再回跳:到点这件事已经被消费掉
     * (截止点清空、读数归零),把回跳而不是暂停交出去等于让定时静默失效。
     * 暂停之后 `isPlaying` 为假,AB 那一支自己就不成立了;用户续播后下一拍照常回跳。
     */
    fun tick(nowElapsedMs: Long, positionMs: Long, isPlaying: Boolean): TickAction {
        val sleepAction = tickSleep(nowElapsedMs)
        if (sleepAction != TickAction.None) return sleepAction
        return abSeekAction(positionMs, isPlaying)
    }

    /** AB 到点回跳;环上任何一环不成立(总开关关、没设全 A/B、停播中、还没到 B 点)都不动播放头 */
    private fun abSeekAction(positionMs: Long, isPlaying: Boolean): TickAction {
        val start = state.abStartMs
        val end = state.abEndMs
        val loopArmed = abLoopEnabled && start != null && end != null && isPlaying
        return if (loopArmed && positionMs >= end) TickAction.SeekTo(start) else TickAction.None
    }

    private fun tickSleep(nowElapsedMs: Long): TickAction {
        val deadline = sleepDeadlineElapsedMs ?: return TickAction.None
        val remaining = deadline - nowElapsedMs
        if (remaining <= 0L) {
            sleepDeadlineElapsedMs = null
            state = state.copy(sleepRemainingMs = null)
            return TickAction.Pause
        }
        val shown = state.sleepRemainingMs
        // 整秒变了才重发:200ms 一拍里同一秒会刷五次同一个数,那些重组对屏上毫无意义。
        // 按"秒的那一位"比,而不是按"差够不够一秒"比 —— 后者会让读数最多滞后一秒才跳。
        if (shown == null || shown / MS_PER_SECOND != remaining / MS_PER_SECOND) {
            state = state.copy(sleepRemainingMs = remaining)
        }
        return TickAction.None
    }

    companion object {
        const val DEFAULT_MIN_AB_SPAN_MS = 1_000L
        private const val MS_PER_SECOND = 1_000L
        private const val MS_PER_MINUTE = 60_000L
    }
}
