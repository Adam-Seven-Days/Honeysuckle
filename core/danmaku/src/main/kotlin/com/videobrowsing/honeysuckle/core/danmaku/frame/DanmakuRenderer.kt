package com.videobrowsing.honeysuckle.core.danmaku.frame

import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuEntry
import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuMode
import com.videobrowsing.honeysuckle.core.danmaku.schedule.DanmakuScheduler
import com.videobrowsing.honeysuckle.core.danmaku.schedule.PlacementResult

/** 相邻两帧之间"不算 seek"的最大跨度:再大就是拖进度条或缓冲补位,而不是掉了一帧 */
private const val CONTINUOUS_STEP_MS = 1_000L

/**
 * 待发插入的队列上限(internal:同模块的帧驱动要按它判断"队列是否已满")。
 *
 * 队列只在"护栏已满的那几帧"里存货:滚动轨每一帧都在放人,补位是常态而非等待。
 * 给一个上限是为了不让"用户连点发送"把队列变成第二条时间轴 —— 满了就当场拒掉,
 * 让调用方有机会说一句"屏幕太挤",比无限攒着一堆永远不会上屏的条目诚实。
 */
internal const val MAX_PENDING_INSERTIONS = 32

/** 实时插入的三种去向,见 [DanmakuRenderer.insert] */
enum class InsertOutcome {
    /** 当场占到轨道,本帧已上屏 */
    PLACED,

    /** 此刻轨道已满,已收进待发队列,后续帧里一有空轨就补(先到先补) */
    QUEUED,

    /** 这条不会上屏:待发队列也满了,或这个 id 已经认领过 */
    REJECTED,
}

/**
 * 弹幕帧驱动:把"一条全局时间轴 + 一个播放器时钟"变成一帧的绘制指令列表。
 *
 * 这是"内核每帧输出本帧应上屏的条目 + 坐标"的那个输出端,也是
 * [DanmakuScheduler] 的喂入契约(timeMs 非递减)在真实播放场景里的**唯一守护者**:
 * seek 会让时间轴往回走,而调度器只允许往前走,所以这里对跳变一律"清屏 + 重捞",
 * 而不是把逆序喂进去等它抛 [com.videobrowsing.honeysuckle.core.danmaku.schedule.EntryOutOfOrderException]。
 *
 * 三条硬口径:
 *  - **零分配**:返回值里的 [DrawItem] 全部来自对象池,跨帧复用同一批实例;调用方只在
 *    主线程读、且只在本帧内读。
 *  - **幂等**:同一个 positionMs 连调两次不会多投同一条、也不会多计一次数(Compose 的
 *    draw lambda 不保证只执行一次,这条是安全前提)。
 *  - **纯函数于视频时间轴**:位置 = `f(entry.enterMs, positionMs)`,与墙上时钟无关,
 *    所以暂停即冻结、倍速不失真、seek 可复现。
 *
 * 时间轴之外的入口只有一个:[insert] —— 发弹幕回流那种"播放头此刻才出现"的条目等不到游标走到它
 * (游标只往前走,而它落在游标身后),这个代价已有记录。
 *
 * 函数数超阈值是有意的:帧驱动、seek 回捞与实时插入队列共用同一只对象池与同一张认领表,
 * 拆成协作类反而要把 board/claimedIds 的访问面公开出去。
 */
@Suppress("TooManyFunctions")
class DanmakuRenderer(private val measurer: DanmakuTextMeasurer) {

    /** 本帧应上屏的条目;[frame] 返回值与它是同一个实例 */
    val items: List<DrawItem> get() = board.items

    /** 当前在屏条数 */
    val onScreenCount: Int get() = board.onScreenCount

    /** 累计上屏条数(去重口径:同一条被 seek 重捞不重复计) */
    var shownTotal = 0
        private set

    /** 累计"同屏护栏已满"被挡下的条数 —— 设计要求这笔账对 UI 可见,不许静默消失 */
    var droppedByBudget = 0
        private set

    /** 累计"载荷缺失、无法上屏"的 mode7 条数(带载荷的自由定位正常渲染,不计入此账) */
    var bypassedFreeLocate = 0
        private set

    /** 还在等空轨的实时插入条数(发弹幕那一侧的"排队中",UI 可据此给一句反馈) */
    val pendingInsertionCount: Int get() = pendingInsertions.size

    /** 上一次 [frame] 的视频位置,宿主用它判断"画面没动就别重绘" */
    var lastPositionMs = Long.MIN_VALUE
        private set

    internal val poolSize: Int get() = board.pooledCount

    /** 在屏集合与它的对象池:领牌、逐帧改写坐标、出屏回池全在这一侧(见 `DrawItemBoard`) */
    private val board = DrawItemBoard()

    private var entries: List<DanmakuEntry> = emptyList()
    private var cursor = 0

    /** 轴里自由定位条目最长的生存期:回捞窗口至少要盖过它,否则 seek 后活着的 mode7 捞不回来 */
    private var maxFreeLocateLifeMs = 0L

    /** 游标到过的最大下标:只有"首次经过"的条目才计入统计,seek 回捞是同一批的二次经过 */
    private var maxFedIndex = 0

    /** 已交给调度器的最大 timeMs:换轴后从它之后续喂,免得把在飞的那条再投一遍 */
    private var lastFedTimeMs = Long.MIN_VALUE

    /**
     * 实时插入认领过的弹幕 id。
     *
     * 插入的那条随后会由分片重新合并进时间轴(平台真身),游标再次经过它时不许再投第二份。
     * 之所以按 id 认领而不是靠 [lastFedTimeMs] 抬水位:插入的时刻可能落在游标之前,
     * 抬水位会把同一区间里其他还没喂的条目一起跳掉。
     * 没有 id 的条目(平台未下发,兜底成 [NO_ENTRY_ID])不认领:那个 0 是"缺省"而不是"同一条"。
     */
    private val claimedIds = mutableSetOf<Long>()

    /** 护栏已满、暂时补不进去的实时插入;先到先补,seek 不清空(它在等的是空轨,不是这一屏) */
    private val pendingInsertions = ArrayDeque<DanmakuEntry>()

    private var scheduler: DanmakuScheduler? = null
    private var layout: FrameLayout? = null

    /**
     * 换上新的全局时间轴([merge/DanmakuTimeline] 的产出),续喂点按"已经交到哪儿"重算。
     *
     * 已在飞的条目不受影响:它们的坐标由 `enterMs` 推出,与新轴无关。
     * 排序是防御性的 —— 契约上 merge 已给升序(`sortedBy` 稳定,对有序输入是 O(n)),
     * 但调用方若是直接把分片首尾相接,那就不该等到绘制阶段才炸出逆序异常。
     *
     * 用时间水位([lastFedTimeMs])而不是播放头或下标来定位续喂点,是因为这两者都会漂:
     * 新片里的更早条目会把下标整体后移,而播放头在 seek 后可能已经退到已投区之间。
     * 已知代价:换轴后**与水位同一毫秒**的新条目会被当成"已经投过"跳过 —— 同刻的条目本就
     * 在一帧内一起投完,所以这只发生在"分片恰好在播放头那一毫秒补到"的边界上。
     */
    fun setTimeline(entries: List<DanmakuEntry>) {
        val sorted = entries.sortedBy { it.timeMs }
        this.entries = sorted
        maxFreeLocateLifeMs = sorted.maxOfOrNull { it.freeLocate?.lifeMs ?: 0L } ?: 0L
        cursor = sorted.firstIndexAfter(lastFedTimeMs)
        maxFedIndex = cursor
    }

    /**
     * 实时插入一条弹幕:发弹幕回流、实时推送那种"此刻才出现"的条目走这一条口。
     *
     * 为什么不能让 [setTimeline] 带它进来:游标只往前走,而这条的时间就在播放头上,
     * 落在游标身后 —— 换轴时它还会被 [lastFedTimeMs] 那道水位当成"已经投过"跳掉。
     * 这个代价已有记录,这一口是它的解。
     *
     * 三条口径:
     *  - **入场时刻不看它自己带的时间**:改取播放头(不早于调度器水位)。理由见 [placeInserted],
     *    落到观感上就是"我发的那条从右边缘擦入",而不是"凭空出现在屏幕中间"。
     *  - **按 id 认领**:认领过的那条,以后随分片重新合并进来时游标不再投第二遍。
     *    seek 会放开认领(那一屏重捞,真身可以在原时刻出现),但排队中的仍被认领。
     *  - **满员不硬塞**:轨道此刻全满就先排队,后续每一帧都试一次;队列也满了才当场拒掉。
     *
     * 只在主线程调用(与 [frame] 同一线程):它改的是同一批池化对象,不是快照状态。
     */
    fun insert(entry: DanmakuEntry, positionMs: Long): InsertOutcome {
        if (entry.id != NO_ENTRY_ID && entry.id in claimedIds) return InsertOutcome.REJECTED

        if (entry.mode == DanmakuMode.FREE_LOCATE) {
            bypassedFreeLocate++
            return InsertOutcome.REJECTED
        }

        val scheduler = this.scheduler
        val layout = this.layout
        // 视口还没量出来时连宽度与轨道数都不知道,只能先存货,等第一帧再补
        val placed = scheduler != null && layout != null && placeInserted(entry, positionMs, layout, scheduler)
        if (!placed) return queueForInsertion(entry)

        // 上屏即认领;排队中的由 queueForInsertion 认领,而队列满了那条**不认领** ——
        // 它没挤进本地这一屏,但真身以后随分片回来时该按正常轴走一遍,不该被无声挡掉
        if (entry.id != NO_ENTRY_ID) claimedIds.add(entry.id)
        return InsertOutcome.PLACED
    }

    /**
     * 走一帧。
     *
     * @param positionMs 播放器时钟的视频位置
     * @param viewport 绘制区域尺寸(旋转/分屏会变,变了本帧清屏重捞)
     * @return 本帧应上屏的 [items];视口还没量出来时原样返回上一帧,不做任何判定
     */
    fun frame(positionMs: Long, viewport: DanmakuViewport, style: DanmakuStyle): List<DrawItem> {
        if (viewport.widthPx <= 0f || viewport.heightPx <= 0f) return board.items

        val next = FrameLayout.of(viewport, style)
        val rewound = needsRewind(positionMs, next)
        if (rewound) rewind(positionMs, next)
        feedUpTo(positionMs, next)
        drainPendingInsertions(positionMs, next)
        board.advance(positionMs, next)
        lastPositionMs = positionMs
        return board.items
    }

    private fun needsRewind(positionMs: Long, next: FrameLayout): Boolean {
        val current = layout
        return scheduler == null || current == null ||
            current != next ||
            lastPositionMs == Long.MIN_VALUE ||
            positionMs < lastPositionMs ||
            positionMs - lastPositionMs > CONTINUOUS_STEP_MS
    }

    /** 清屏 + 换调度器 + 把续喂水位归零,再把游标退到"还在路上"的窗口头,正向重捞 */
    private fun rewind(positionMs: Long, next: FrameLayout) {
        board.clear()
        layout = next
        lastFedTimeMs = Long.MIN_VALUE
        // seek 后在屏归零,时间轴里的真身允许在这个位置重新出现,所以认领要放开;
        // 还在排队的那几条不放 —— 它们等的是空轨,与换到哪一帧无关
        claimedIds.retainAll(pendingInsertions.mapTo(mutableSetOf()) { it.id })
        scheduler = DanmakuScheduler(
            viewWidthPx = next.widthPx.toInt().coerceAtLeast(1),
            speedPxPerSec = next.style.speedPxPerSec,
            scrollTrackCount = next.counts.scroll,
            topTrackCount = next.counts.top,
            bottomTrackCount = next.counts.bottom,
            fixedDurationMs = next.style.fixedDurationMs,
        )
        cursor = entries.firstIndexAtOrAfter(
            minOf(next.primeFromMs(positionMs), positionMs - maxFreeLocateLifeMs).coerceAtLeast(0L),
        )
    }

    private fun feedUpTo(positionMs: Long, layout: FrameLayout) {
        val scheduler = this.scheduler ?: return
        while (cursor < entries.size && entries[cursor].timeMs <= positionMs) {
            val index = cursor
            val entry = entries[index]
            cursor++
            // 水位只在"重算游标"时用来防重投,循环自身按下标往前走,同刻多条不会被误跳
            if (entry.timeMs > lastFedTimeMs) lastFedTimeMs = entry.timeMs
            place(entry, index, positionMs, layout, scheduler)
        }
    }

    /** 把排队的实时插入按"先到先补"放上空轨;有一条还补不进去就整队停住,不许后来者抢位 */
    private fun drainPendingInsertions(positionMs: Long, layout: FrameLayout) {
        val scheduler = this.scheduler ?: return
        while (pendingInsertions.isNotEmpty()) {
            if (!placeInserted(pendingInsertions.first(), positionMs, layout, scheduler)) return
            pendingInsertions.removeFirst()
        }
    }

    private fun place(
        entry: DanmakuEntry,
        index: Int,
        positionMs: Long,
        layout: FrameLayout,
        scheduler: DanmakuScheduler,
    ) {
        // 实时插入已经认领过的那条,游标再次经过时不再投第二份(它已经在屏上或还在队列里)
        if (entry.id != NO_ENTRY_ID && entry.id in claimedIds) return

        val counted = index >= maxFedIndex
        if (counted) maxFedIndex = index + 1

        // mode7 分两路:载荷在 → 自由定位层上屏;
        // 载荷不在(适配层没解出的旧轴)→ 保持旁路留数,UI 可提示
        if (entry.mode == DanmakuMode.FREE_LOCATE) {
            placeFreeLocate(entry, counted, positionMs, layout)
            return
        }
        placeTracked(entry, counted, positionMs, layout, scheduler)
    }

    private fun placeFreeLocate(entry: DanmakuEntry, counted: Boolean, positionMs: Long, layout: FrameLayout) {
        if (entry.freeLocate == null) {
            if (counted) bypassedFreeLocate++
            return
        }
        val size = measurer.measure(entry.text, entry.fontSize * layout.style.fontScale)
        board.showFreeLocate(entry, size, positionMs, layout)
        if (counted) shownTotal++
    }

    private fun placeTracked(
        entry: DanmakuEntry,
        counted: Boolean,
        positionMs: Long,
        layout: FrameLayout,
        scheduler: DanmakuScheduler,
    ) {
        val size = measurer.measure(entry.text, entry.fontSize * layout.style.fontScale)
        // 与调度器用同一个宽度做碰撞判定,否则算出的 x 和判出的间距会错位
        when (val placement = scheduler.place(entry, size.widthPx.coerceAtMost(layout.widthPx))) {
            is PlacementResult.Placed -> {
                val track = placement.placement
                if (layout.worthShowing(entry.timeMs, track.lane, size, positionMs)) {
                    board.show(entry, size, track, positionMs, layout)
                    if (counted) shownTotal++
                }
            }

            PlacementResult.Dropped -> if (counted) droppedByBudget++

            PlacementResult.Bypassed -> if (counted) bypassedFreeLocate++
        }
    }

    /**
     * 试着让一条实时插入当场占到轨道。
     *
     * 入场时刻取"播放头与调度器水位的较大者",而不是这条自带的时间:碰撞判定成不成立,取决于
     * **新条比所有在飞的都新**(调度器只比本轨最近入场的那条,见 `schedule/DanmakuScheduler`),
     * 而回流那条的时间常在播放头之前几百毫秒(按下发送时的那一格),照原值投就落在判定成立的范围外。
     * 夹在这儿还顺带保住了一件事:还没喂的条目时间都在播放头之后,必然也在这个值之后,
     * 于是游标后续的喂入不会因为一次插入变成逆序喂入。
     *
     * 夹完之后 `enterMs ≥ positionMs`,擦入口径下此刻的 x 正好是屏宽 —— [FrameLayout.worthShowing]
     * 在这一路恒为真,所以不必再调它。
     *
     * @return 没占到轨道(护栏已满)返回 false,由调用方排队重试
     */
    private fun placeInserted(
        entry: DanmakuEntry,
        positionMs: Long,
        layout: FrameLayout,
        scheduler: DanmakuScheduler,
    ): Boolean {
        val enterMs = maxOf(positionMs, scheduler.lastAcceptedTimeMs)
        val stamped = if (enterMs == entry.timeMs) entry else entry.copy(timeMs = enterMs)
        val size = measurer.measure(stamped.text, stamped.fontSize * layout.style.fontScale)
        val result = scheduler.place(stamped, size.widthPx.coerceAtMost(layout.widthPx))
        val placement = (result as? PlacementResult.Placed)?.placement ?: return false
        board.show(stamped, size, placement, positionMs, layout)
        shownTotal++
        return true
    }

    private fun queueForInsertion(entry: DanmakuEntry): InsertOutcome {
        if (pendingInsertions.size >= MAX_PENDING_INSERTIONS) {
            // 归进"护栏挡下"那一笔:补不进去的原因就是同屏已满,不是另一种故障
            droppedByBudget++
            return InsertOutcome.REJECTED
        }
        pendingInsertions.addLast(entry)
        if (entry.id != NO_ENTRY_ID) claimedIds.add(entry.id)
        return InsertOutcome.QUEUED
    }
}
