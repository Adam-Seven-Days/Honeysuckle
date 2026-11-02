package com.videobrowsing.honeysuckle.core.qrcode

import kotlin.math.abs

private const val RUN_MIN = 5
private const val RUN_WEIGHT = 3
private const val BLOCK_WEIGHT = 3
private const val FINDER_WEIGHT = 40
private const val BALANCE_WEIGHT = 10
private const val PERCENT_STEPS = 10
private const val FINDER_WINDOW = 11
private const val FINDER_CORE = 7
private const val LIGHT_AREA = 4
private const val PATTERN_LIGHT_FIRST = 0b00001011101
private const val PATTERN_DARK_FIRST = 0b10111010000

/**
 * 掩码罚分(ISO/IEC 18004 的 N1–N4):
 *  - 连续同色 ≥5 扣 3 + 超出部分;
 *  - 每个 2×2 同色块扣 3;
 *  - 形似定位图形(1011101 前或后有 4 个浅色)每种扣 40;
 *  - 黑块占比每偏离 50% 满 5 个百分点扣 10。
 */
internal object QrPenalty {
    fun score(grid: QrGrid): Int = runs(grid) + blocks(grid) + finderLike(grid) + balance(grid)

    private fun runs(grid: QrGrid): Int {
        var penalty = 0
        for (y in 0 until grid.size) penalty += lineRuns(grid.size) { x -> grid.isDark(x, y) }
        for (x in 0 until grid.size) penalty += lineRuns(grid.size) { y -> grid.isDark(x, y) }
        return penalty
    }

    private inline fun lineRuns(size: Int, isDark: (Int) -> Boolean): Int {
        var penalty = 0
        var run = 1
        var previous = isDark(0)
        for (index in 1 until size) {
            val current = isDark(index)
            if (current == previous) {
                run++
            } else {
                if (run >= RUN_MIN) penalty += RUN_WEIGHT + run - RUN_MIN
                run = 1
                previous = current
            }
        }
        if (run >= RUN_MIN) penalty += RUN_WEIGHT + run - RUN_MIN
        return penalty
    }

    private fun blocks(grid: QrGrid): Int {
        var penalty = 0
        for (y in 0 until grid.size - 1) {
            for (x in 0 until grid.size - 1) {
                val dark = grid.isDark(x, y)
                val same = dark == grid.isDark(x + 1, y) &&
                    dark == grid.isDark(x, y + 1) &&
                    dark == grid.isDark(x + 1, y + 1)
                if (same) penalty += BLOCK_WEIGHT
            }
        }
        return penalty
    }

    private fun finderLike(grid: QrGrid): Int {
        var penalty = 0
        for (y in 0 until grid.size) penalty += lineFinderLike(grid.size) { x -> grid.isDark(x, y) }
        for (x in 0 until grid.size) penalty += lineFinderLike(grid.size) { y -> grid.isDark(x, y) }
        return penalty
    }

    /** 窗口允许伸出矩阵边界:边界外按静区算浅色,与标准里"前后留 4 个浅色"同义。 */
    private inline fun lineFinderLike(size: Int, isDark: (Int) -> Boolean): Int {
        var penalty = 0
        for (start in -LIGHT_AREA..size - FINDER_CORE) {
            var window = 0
            for (offset in 0 until FINDER_WINDOW) {
                val index = start + offset
                val dark = index in 0 until size && isDark(index)
                window = (window shl 1) or (if (dark) 1 else 0)
            }
            if (window == PATTERN_LIGHT_FIRST || window == PATTERN_DARK_FIRST) penalty += FINDER_WEIGHT
        }
        return penalty
    }

    private fun balance(grid: QrGrid): Int {
        var dark = 0
        for (cell in grid.cells) if (cell) dark++
        val total = grid.size * grid.size
        val steps = abs(dark * 2 - total) * PERCENT_STEPS / total
        return steps * BALANCE_WEIGHT
    }
}
