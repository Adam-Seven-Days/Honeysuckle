package com.videobrowsing.honeysuckle.core.qrcode

private const val MASK_COUNT = 8
private const val BITS_PER_BYTE = 8
private const val TIMING_LINE = 6

internal class Drawn(val mask: Int, val cells: BooleanArray)

/**
 * 数据落位与掩码:方案是从右下往上蛇形填两列,跳过第 6 列时序线;
 * 八种掩码各画一遍,按罚分挑最低的一张(罚分见 [QrPenalty])。
 */
internal object QrMasks {
    fun choose(base: QrGrid, ecc: QrEcc, codewords: IntArray): Drawn {
        var best: Drawn? = null
        var bestScore = Int.MAX_VALUE
        for (mask in 0 until MASK_COUNT) {
            val candidate = base.copy()
            placeData(candidate, codewords, mask)
            QrPatterns.placeFormat(candidate, ecc, mask)
            val score = QrPenalty.score(candidate)
            if (score < bestScore) {
                bestScore = score
                best = Drawn(mask, candidate.cells)
            }
        }
        return checkNotNull(best) { "八种掩码都没有产出矩阵" }
    }

    private fun placeData(grid: QrGrid, codewords: IntArray, mask: Int) {
        val writer = DataWriter(grid, codewords, mask)
        var column = grid.size - 1
        var upward = true
        while (column > 0) {
            if (column == TIMING_LINE) column--
            writer.fillColumn(column, upward)
            upward = !upward
            column -= 2
        }
    }
}

/** 蛇形填格:一列一对,方向逐列翻转;功能图形与非数据区一律跳过。 */
private class DataWriter(private val grid: QrGrid, private val codewords: IntArray, private val mask: Int) {
    private val totalBits = codewords.size * BITS_PER_BYTE
    private var bit = 0

    fun fillColumn(column: Int, upward: Boolean) {
        for (step in 0 until grid.size) {
            val y = if (upward) grid.size - 1 - step else step
            fillPair(column, y)
        }
    }

    private fun fillPair(column: Int, y: Int) {
        for (offset in 0..1) {
            val x = column - offset
            if (grid.isFixed(x, y)) continue
            val data = bit < totalBits && bitAt(bit)
            grid.setDark(x, y, data != maskBitAt(mask, x, y))
            bit++
        }
    }

    private fun bitAt(index: Int): Boolean {
        val byte = codewords[index / BITS_PER_BYTE]
        return (byte ushr (BITS_PER_BYTE - 1 - index % BITS_PER_BYTE)) and 1 != 0
    }
}

@Suppress("MagicNumber") // 掩码公式是标准里的原式,拆成命名常量只会看不出对应关系
private fun maskBitAt(mask: Int, x: Int, y: Int): Boolean = when (mask) {
    0 -> (x + y) % 2 == 0
    1 -> y % 2 == 0
    2 -> x % 3 == 0
    3 -> (x + y) % 3 == 0
    4 -> (y / 2 + x / 3) % 2 == 0
    5 -> (x * y) % 2 + (x * y) % 3 == 0
    6 -> ((x * y) % 2 + (x * y) % 3) % 2 == 0
    else -> ((x + y) % 2 + (x * y) % 3) % 2 == 0
}
