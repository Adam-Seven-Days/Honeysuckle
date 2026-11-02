package com.videobrowsing.honeysuckle.core.player.danmaku

import com.videobrowsing.honeysuckle.core.danmaku.filter.FilterEngine
import com.videobrowsing.honeysuckle.core.danmaku.model.DanmakuEntry

/** [DanmakuScreener.screen] 的产出:一条放行轴 + 两笔互不合并的账 */
data class ScreeningResult(
    val passed: List<DanmakuEntry>,
    /** 被屏蔽词挡下的条数 */
    val hiddenByFilter: Int,
    /** 被投放速率挡下的条数 */
    val hiddenByDensity: Int,
)

/**
 * 屏蔽 + 密度两道闸门的合成 —— 那条"分片 → 屏蔽 → 投放 → 合并 → 内核"链上,
 * 前两道在这一层落地([FilterEngine] 与 [DanmakuAdmission] 各自单独存在,这里只定先后)。
 *
 * **顺序只有一处讲究:屏蔽在前、密度在后。** 反过来让屏蔽词先去抢通行券,用户加一个屏蔽词
 * 就等于把自己的密度档调暗一格 —— 屏幕上少掉的是本来能看见的那些条,而账面上看不见这笔损耗从哪来。
 * 屏蔽掉的不该占额度,就像不来的观众不该占座位。
 *
 * 两道账分列不合并:"这条被你自己屏蔽了"和"这条被密度闸门挡了"
 * 是两句不同的话,合并成一个数就没法解释屏幕上为什么少了东西。
 *
 * 与 [DanmakuAdmission] 一样纯函数于时间轴:不看墙上时钟,所以倍速不失真、seek 重算即复现。
 *
 * @param blockedWords 用户存的屏蔽词原样(清洗在 [DanmakuBlocklist] 里做)。
 *   词表变了就换一个实例:automaton 在构造期一次建完,查询侧不再碰词表。
 */
class DanmakuScreener(blockedWords: Collection<String>) {

    private val rules = DanmakuBlocklist.rules(blockedWords)
    private val engine = FilterEngine(rules)

    /** 词表清洗完一条不剩时,连归一化都不必做:整条时间轴原样交给密度闸门 */
    private val filtersNothing = rules.isEmpty()

    fun screen(timeline: List<DanmakuEntry>, density: DanmakuDensity): ScreeningResult {
        val survivors = if (filtersNothing) timeline else timeline.filterNot { engine.isBlocked(it.text) }
        val admission = DanmakuAdmission.admit(survivors, density)
        return ScreeningResult(
            passed = admission.passed,
            hiddenByFilter = timeline.size - survivors.size,
            hiddenByDensity = admission.hidden,
        )
    }
}
